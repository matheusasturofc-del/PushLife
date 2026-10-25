package com.pushlife.app.core.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlocklistTest {
    private val list = DomainBlocklist()

    @Test
    fun bloqueiaDominioESubdominios() {
        assertTrue(list.isBlocked("pornhub.com"))
        assertTrue(list.isBlocked("www.pornhub.com"))
        assertTrue(list.isBlocked("cdn.static.NHENTAI.net."))
    }

    @Test
    fun palavrasChavePegamEspelhos() {
        assertTrue(list.isBlocked("novo-site-porn.biz"))
        assertTrue(list.isBlocked("hentaiworld.example"))
    }

    @Test
    fun naoBloqueiaSitesNormais() {
        for (ok in listOf("google.com", "youtube.com", "essex.ac.uk", "sussex.com", "instagram.com", "wikipedia.org", "nothub.com")) {
            assertFalse(ok, list.isBlocked(ok))
        }
    }
}

class DnsTest {
    // consulta tipo A, igual um celular mandaria
    private fun query(name: String, id: Int = 0x1234, withEdns: Boolean = false): ByteArray {
        val out = mutableListOf<Byte>()
        fun u16(v: Int) { out += (v ushr 8).toByte(); out += v.toByte() }
        u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(if (withEdns) 1 else 0)
        name.split('.').forEach { label -> out += label.length.toByte(); label.forEach { out += it.code.toByte() } }
        out += 0
        u16(1); u16(1) // tipo A, classe IN
        if (withEdns) { out += 0; u16(41); u16(4096); u16(0); u16(0); u16(0) }
        return out.toByteArray()
    }

    @Test
    fun leONomeDaPergunta() {
        assertEquals("www.xvideos.com", Dns.questionName(query("www.xvideos.com")))
        assertNull(Dns.questionName(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun respostaNxDomainMantemIdEPergunta() {
        val q = query("pornhub.com", id = 0xBEEF, withEdns = true)
        val r = Dns.nxDomain(q)!!
        assertEquals(0xBEEF, Packets.u16(r, 0))
        assertTrue("é resposta", r[2].toInt() and 0x80 != 0)
        assertTrue("pediu recursão (RD) e ela continua marcada", r[2].toInt() and 0x01 != 0)
        assertEquals(3, r[3].toInt() and 0x0F) // NXDOMAIN
        assertEquals(1, Packets.u16(r, 4)) // 1 pergunta
        assertEquals(0, Packets.u16(r, 10)) // registro EDNS removido
        assertEquals("pornhub.com", Dns.questionName(r))
        assertEquals(12 + 13 + 4, r.size) // cabeçalho + nome + tipo/classe
    }
}

class PacketsTest {
    @Test
    fun montaELeUmPacoteUdp() {
        val src = byteArrayOf(10, 111, 0, 2)
        val dst = byteArrayOf(10, 111, 0, 1)
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val p = Packets.buildUdp(src, dst, 40000, 53, payload)
        val parsed = Packets.parseUdp(p, p.size)!!
        assertArrayEquals(src, parsed.sourceIp)
        assertArrayEquals(dst, parsed.destinationIp)
        assertEquals(40000, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun checksumDoCabecalhoIpConfere() {
        val p = Packets.buildUdp(byteArrayOf(1, 2, 3, 4), byteArrayOf(5, 6, 7, 8), 1, 2, ByteArray(10))
        assertEquals(0, Packets.checksum(p, 0, 20)) // cabeçalho válido soma 0xFFFF → complemento 0
    }

    @Test
    fun ignoraPacotesQueNaoSaoUdpIpv4() {
        val tcp = Packets.buildUdp(byteArrayOf(1, 2, 3, 4), byteArrayOf(5, 6, 7, 8), 1, 2, ByteArray(4)).also { it[9] = 6 }
        assertNull(Packets.parseUdp(tcp, tcp.size))
        assertNull(Packets.parseUdp(ByteArray(10), 10))
    }
}
