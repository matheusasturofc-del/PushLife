package com.pushlife.app.core.filter

/** Um pacote UDP sobre IPv4 lido da VPN (ou a ser escrito nela). */
class UdpPacket(
    val sourceIp: ByteArray,
    val destinationIp: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

/** Leitura e montagem de pacotes IPv4/UDP, o mínimo para responder consultas de DNS. */
object Packets {
    private const val UDP = 17

    fun parseUdp(packet: ByteArray, length: Int): UdpPacket? {
        if (length < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4 || packet[9].toInt() != UDP) return null
        val ipHeader = (packet[0].toInt() and 0xF) * 4
        if (length < ipHeader + 8) return null
        val udpLength = u16(packet, ipHeader + 4)
        val payloadEnd = minOf(length, ipHeader + udpLength)
        if (payloadEnd < ipHeader + 8) return null
        return UdpPacket(
            sourceIp = packet.copyOfRange(12, 16),
            destinationIp = packet.copyOfRange(16, 20),
            sourcePort = u16(packet, ipHeader),
            destinationPort = u16(packet, ipHeader + 2),
            payload = packet.copyOfRange(ipHeader + 8, payloadEnd),
        )
    }

    /** Monta um pacote IPv4/UDP (sem checksum de UDP, que é opcional no IPv4). */
    fun buildUdp(sourceIp: ByteArray, destinationIp: ByteArray, sourcePort: Int, destinationPort: Int, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45 // IPv4, cabeçalho de 20 bytes
        put16(p, 2, total)
        p[6] = 0x40 // não fragmentar
        p[8] = 64 // TTL
        p[9] = UDP.toByte()
        sourceIp.copyInto(p, 12)
        destinationIp.copyInto(p, 16)
        put16(p, 10, checksum(p, 0, 20))
        put16(p, 20, sourcePort)
        put16(p, 22, destinationPort)
        put16(p, 24, 8 + payload.size)
        payload.copyInto(p, 28)
        return p
    }

    fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += u16(data, i)
            i += 2
        }
        if (length % 2 == 1) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }
}

/** O mínimo de DNS: ler o nome perguntado e responder "não existe" (NXDOMAIN). */
object Dns {
    private const val HEADER = 12

    /** O nome da primeira pergunta ("www.site.com"), ou null se a mensagem for inválida. */
    fun questionName(message: ByteArray): String? = questionEnd(message)?.let { readName(message) }

    /** Resposta "domínio não existe" para a consulta, com a pergunta original. */
    fun nxDomain(query: ByteArray): ByteArray? {
        val end = questionEnd(query) ?: return null
        val r = query.copyOf(end)
        r[2] = ((query[2].toInt() and 0x79) or 0x80).toByte() // QR=1, mantém opcode e RD
        r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        for (i in 6 until 12) r[i] = 0 // sem respostas nem registros extras
        return r
    }

    private fun readName(m: ByteArray): String? {
        val labels = mutableListOf<String>()
        var i = HEADER
        while (i < m.size) {
            val len = m[i].toInt() and 0xFF
            if (len == 0) return labels.joinToString(".")
            if (len and 0xC0 != 0 || i + 1 + len > m.size) return null // perguntas não usam compressão
            labels += String(m, i + 1, len, Charsets.US_ASCII)
            i += 1 + len
        }
        return null
    }

    /** Onde termina a primeira pergunta (nome + tipo + classe). */
    private fun questionEnd(m: ByteArray): Int? {
        if (m.size < HEADER + 5 || Packets.u16(m, 4) < 1) return null
        var i = HEADER
        while (i < m.size) {
            val len = m[i].toInt() and 0xFF
            if (len == 0) return (i + 1 + 4).takeIf { it <= m.size }
            if (len and 0xC0 != 0) return null
            i += 1 + len
        }
        return null
    }
}
