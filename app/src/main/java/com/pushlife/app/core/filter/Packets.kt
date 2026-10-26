package com.pushlife.app.core.filter

// pacote UDP/IPv4 que entra ou sai da VPN
class UdpPacket(
    val sourceIp: ByteArray,
    val destinationIp: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

// IPv4/UDP no mínimo, só pra responder DNS
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

    // monta o pacote (sem checksum UDP, no IPv4 é opcional)
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

// DNS no mínimo: lê o nome e responde NXDOMAIN
object Dns {
    private const val HEADER = 12

    // nome da 1ª pergunta ("www.site.com"); null se a mensagem vier zoada
    fun questionName(message: ByteArray): String? = questionEnd(message)?.let { readName(message) }

    // resposta "não existe" mantendo a pergunta original
    fun nxDomain(query: ByteArray): ByteArray? {
        val end = questionEnd(query) ?: return null
        val r = query.copyOf(end)
        r[2] = ((query[2].toInt() and 0x79) or 0x80).toByte() // QR=1, mantém opcode e RD
        r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        for (i in 6 until 12) r[i] = 0 // sem respostas nem registros extras
        return r
    }

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    // tipo da 1ª pergunta (1 = A/IPv4, 28 = AAAA/IPv6, 65 = HTTPS...)
    fun questionType(message: ByteArray): Int? = questionEnd(message)?.let { Packets.u16(message, it - 4) }

    // pergunta nova, do zero
    fun query(id: Int, name: String, type: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0)) // RD=1, 1 pergunta
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(byteArrayOf(0, (type shr 8).toByte(), type.toByte(), 0, 1))
        return out.toByteArray()
    }

    // endereços (A ou AAAA) que vieram numa resposta, pulando CNAMEs no caminho
    fun addresses(response: ByteArray, type: Int): List<ByteArray> {
        if (response.size < HEADER) return emptyList()
        var i = HEADER
        repeat(Packets.u16(response, 4)) { i = skipName(response, i) + 4 }
        val found = mutableListOf<ByteArray>()
        repeat(Packets.u16(response, 6)) {
            i = skipName(response, i)
            if (i + 10 > response.size) return found
            val rType = Packets.u16(response, i)
            val length = Packets.u16(response, i + 8)
            val start = i + 10
            if (start + length > response.size) return found
            if (rType == type) found += response.copyOfRange(start, start + length)
            i = start + length
        }
        return found
    }

    // resposta para a pergunta original com os endereços dados (lista vazia = "não tem desse tipo")
    fun answer(query: ByteArray, type: Int, addresses: List<ByteArray>, ttl: Int = 300): ByteArray? {
        val end = questionEnd(query) ?: return null
        val out = java.io.ByteArrayOutputStream()
        out.write(query, 0, 2) // mesmo id
        out.write(byteArrayOf(((query[2].toInt() and 0x79) or 0x80).toByte(), 0x80.toByte())) // QR=1, RA=1, sem erro
        out.write(byteArrayOf(0, 1, (addresses.size shr 8).toByte(), addresses.size.toByte(), 0, 0, 0, 0))
        out.write(query, HEADER, end - HEADER) // a pergunta
        for (a in addresses) {
            out.write(byteArrayOf(0xC0.toByte(), HEADER.toByte())) // nome = o da pergunta
            out.write(byteArrayOf((type shr 8).toByte(), type.toByte(), 0, 1))
            out.write(byteArrayOf((ttl shr 24).toByte(), (ttl shr 16).toByte(), (ttl shr 8).toByte(), ttl.toByte()))
            out.write(byteArrayOf((a.size shr 8).toByte(), a.size.toByte()))
            out.write(a)
        }
        return out.toByteArray()
    }

    // pula um nome (com ou sem compressão) e devolve onde termina
    private fun skipName(m: ByteArray, start: Int): Int {
        var i = start
        while (i < m.size) {
            val len = m[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if (len and 0xC0 == 0xC0) return i + 2
            i += 1 + len
        }
        return m.size
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

    // fim da 1ª pergunta (nome + tipo + classe)
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
