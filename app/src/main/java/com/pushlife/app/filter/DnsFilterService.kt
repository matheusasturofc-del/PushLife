package com.pushlife.app.filter

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.pushlife.app.AppGraph
import com.pushlife.app.core.filter.Dns
import com.pushlife.app.core.filter.DomainBlocklist
import com.pushlife.app.core.filter.HashedDomains
import com.pushlife.app.core.filter.Packets
import com.pushlife.app.data.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// filtro adulto: VPN local que só pega o DNS (o resto da internet passa por fora)
// site da lista = "não existe", o resto vai pro DNS normal da pessoa, sem serviço de fora
class DnsFilterService : VpnService() {
    // começa com a lista curta, a grande (~1 milhão de sites) carrega em segundo plano
    @Volatile private var blocklist = DomainBlocklist()
    private var dnsServers: List<InetAddress> = emptyList()
    private var dnsServersAt = 0L
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !AppGraph.blocking.state.value.adultFilter) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) startFilter()
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun startFilter() {
        val fd = Builder()
            .setSession("PushLife")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32) // só o tráfego para o "DNS falso" entra na VPN
            .establish() ?: return
        tun = fd
        Thread({ loadBigList() }, "pushlife-blocklist").start()
        workers = Executors.newFixedThreadPool(4)
        reader = Thread({ readLoop(fd) }, "pushlife-dns").also { it.start() }
        _running.value = true
    }

    private fun readLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32_767)
        while (!Thread.currentThread().isInterrupted && tun != null) {
            val length = try {
                input.read(buffer)
            } catch (_: IOException) {
                if (tun == null || !pause()) return // a VPN foi fechada
                continue
            }
            if (length <= 0) {
                if (!pause()) return // Android 8/9: a leitura não bloqueia, então espera
                continue
            }
            val packet = Packets.parseUdp(buffer, length) ?: continue
            if (packet.destinationPort != 53) continue
            // runCatching pq ao desligar o executor pode recusar tarefa nova
            runCatching {
                workers?.execute {
                    val query = packet.payload
                    val name = Dns.questionName(query)
                    val answer = if (name != null && blocklist.isBlocked(name)) Dns.nxDomain(query) else forward(query)
                    if (answer != null) {
                        val reply = Packets.buildUdp(packet.destinationIp, packet.sourceIp, packet.destinationPort, packet.sourcePort, answer)
                        synchronized(output) { runCatching { output.write(reply) } }
                    }
                }
            }
        }
    }

    // espera um pouco; false = thread interrompida (filtro desligando)
    private fun pause(): Boolean = try {
        Thread.sleep(20)
        true
    } catch (_: InterruptedException) {
        false
    }

    private fun loadBigList() {
        val big = runCatching { HashedDomains.read(assets.open("filter/adult_domains.bin").use { it.readBytes() }) }.getOrNull()
        if (big != null) blocklist = DomainBlocklist(big = big)
    }

    // manda a consulta pro DNS de verdade, por fora da VPN
    private fun forward(query: ByteArray): ByteArray? {
        for (server in systemDns()) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4_000
                    socket.send(DatagramPacket(query, query.size, server, 53))
                    val buffer = ByteArray(4_096)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    return buffer.copyOf(response.length)
                }
            } catch (_: IOException) {
                // tenta o próximo servidor
            }
        }
        return null
    }

    // DNS do Wi-Fi/dados móveis (sem a nossa VPN), relê a cada 10 s
    @Suppress("DEPRECATION") // allNetworks: o substituto exige ficar ouvindo mudanças de rede
    private fun systemDns(): List<InetAddress> = synchronized(this) {
        val now = System.currentTimeMillis()
        if (dnsServers.isEmpty() || now - dnsServersAt > 10_000) {
            val cm = getSystemService(ConnectivityManager::class.java)
            val found = cm.allNetworks.flatMap { network ->
                val caps = cm.getNetworkCapabilities(network)
                val usable = caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (usable) cm.getLinkProperties(network)?.dnsServers.orEmpty() else emptyList()
            }.distinct()
            if (found.isNotEmpty()) dnsServers = found
            dnsServersAt = now
        }
        dnsServers
    }

    private fun shutdown() {
        val fd = tun
        tun = null
        reader?.interrupt()
        reader = null
        workers?.shutdownNow()
        workers = null
        runCatching { fd?.close() }
        _running.value = false
    }

    companion object {
        private const val ACTION_STOP = "com.pushlife.app.filter.STOP"
        private const val VPN_ADDRESS = "10.111.0.2"
        private const val FAKE_DNS = "10.111.0.1"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        // liga/desliga conforme o ajuste (só liga se a VPN já foi autorizada)
        fun syncWithSettings(context: Context) {
            val wanted = AppGraph.blocking.state.value.adultFilter
            val intent = Intent(context, DnsFilterService::class.java)
            when {
                wanted && VpnService.prepare(context) == null -> runCatching { context.startService(intent) }
                !wanted && running.value -> runCatching { context.startService(intent.setAction(ACTION_STOP)) }
            }
        }
    }
}
