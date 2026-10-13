package com.pushlife.app.filter

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.pushlife.app.AppGraph
import com.pushlife.app.core.filter.Dns
import com.pushlife.app.core.filter.DomainBlocklist
import com.pushlife.app.core.filter.Packets
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

/**
 * Filtro de sites adultos por DNS, numa VPN local que só recebe as consultas de DNS
 * (o resto da internet não passa por ela). Domínios da lista recebem "não existe";
 * os outros vão para o DNS família da Cloudflare, que também bloqueia conteúdo adulto.
 */
class DnsFilterService : VpnService() {
    private val blocklist = DomainBlocklist()
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null

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
                if (!pause()) return // Android 8 e 9: a leitura não bloqueia, então espera um pouco
                continue
            }
            val packet = Packets.parseUdp(buffer, length) ?: continue
            if (packet.destinationPort != 53) continue
            // runCatching: ao desligar, o executor pode recusar tarefas novas.
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

    /** Espera um instante; false se a thread foi interrompida (filtro desligando). */
    private fun pause(): Boolean = try {
        Thread.sleep(20)
        true
    } catch (_: InterruptedException) {
        false
    }

    /** Repassa a consulta ao DNS família da Cloudflare, por fora da VPN. */
    private fun forward(query: ByteArray): ByteArray? {
        for (server in UPSTREAM) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 4_000
                    socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
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
        private val UPSTREAM = listOf("1.1.1.3", "1.0.0.3") // Cloudflare for Families: bloqueia malware e conteúdo adulto

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Liga ou desliga o filtro conforme o ajuste salvo (só liga se a VPN já foi autorizada). */
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
