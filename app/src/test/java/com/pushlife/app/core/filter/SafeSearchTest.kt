package com.pushlife.app.core.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSearchTest {
    @Test
    fun buscadoresViramAVersaoSegura() {
        assertEquals("forcesafesearch.google.com", SafeSearch.target("www.google.com"))
        assertEquals("forcesafesearch.google.com", SafeSearch.target("www.google.com.br."))
        assertEquals("forcesafesearch.google.com", SafeSearch.target("google.co.uk"))
        assertEquals("strict.bing.com", SafeSearch.target("www.bing.com"))
        assertEquals("safe.duckduckgo.com", SafeSearch.target("duckduckgo.com"))
        assertNull(SafeSearch.target("m.youtube.com")) // sem modo restrito, pra poder comentar
        // o resto do Google segue normal
        assertNull(SafeSearch.target("mail.google.com"))
        assertNull(SafeSearch.target("drive.google.com"))
        assertNull(SafeSearch.target("googleapis.com"))
        assertNull(SafeSearch.target("site.com"))
    }

    @Test
    fun perguntaNovaTemNomeETipoCertos() {
        val q = Dns.query(0x1234, "forcesafesearch.google.com", Dns.TYPE_A)
        assertEquals("forcesafesearch.google.com", Dns.questionName(q))
        assertEquals(Dns.TYPE_A, Dns.questionType(q))
    }

    @Test
    fun pegaOEnderecoDepoisDoCnameEMontaARespostaDaPerguntaOriginal() {
        // resposta do DNS para forcesafesearch.google.com: um CNAME (comprimido) e depois o IPv4
        val q = Dns.query(7, "forcesafesearch.google.com", Dns.TYPE_A)
        val ip = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)
        val resp = q.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = 2 } +
            byteArrayOf(0xC0.toByte(), 12, 0, 5, 0, 1, 0, 0, 0, 60, 0, 2, 0xC0.toByte(), 12) + // CNAME
            byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4) + ip // A
        val found = Dns.addresses(resp, Dns.TYPE_A)
        assertEquals(1, found.size)
        assertArrayEquals(ip, found[0])

        // a pessoa perguntou www.google.com: a resposta é para www.google.com, com o IP da versão segura
        val original = Dns.query(99, "www.google.com", Dns.TYPE_A)
        val answer = Dns.answer(original, Dns.TYPE_A, found)!!
        assertEquals("www.google.com", Dns.questionName(answer))
        assertArrayEquals(ip, Dns.addresses(answer, Dns.TYPE_A)[0])
        assertEquals(99, Packets.u16(answer, 0))
    }

    @Test
    fun outrosTiposRecebemRespostaVazia() {
        val q = Dns.query(5, "www.google.com", 65) // HTTPS: o navegador volta a usar A/AAAA
        val a = Dns.answer(q, 65, emptyList())!!
        assertEquals(0, Packets.u16(a, 6))
        assertEquals(0, a[3].toInt() and 0x0F) // sem erro
    }
}
