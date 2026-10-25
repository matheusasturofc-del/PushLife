package com.pushlife.app.core.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class HashedDomainsTest {
    @Test
    fun hashIgualAoDoGradle() {
        // Valores de referência do FNV-1a 64 (mesmo cálculo do build.gradle.kts).
        assertEquals(-0x340d631b7bdddcdbL, HashedDomains.hash(""))
        assertEquals(-0x509c23b379fe1374L, HashedDomains.hash("a"))
    }

    @Test
    fun achaDominioESubdominioPelaListaGrande() {
        val big = HashedDomains.of(listOf("sitequalquer.com", "outro.net"))
        val list = DomainBlocklist(domains = emptyList(), keywords = emptyList(), big = big)
        assertTrue(list.isBlocked("sitequalquer.com"))
        assertTrue(list.isBlocked("www.SiteQualquer.com."))
        assertTrue(list.isBlocked("cdn.img.outro.net"))
        assertFalse(list.isBlocked("google.com"))
        assertFalse(list.isBlocked("naositequalquer.com"))
    }

    @Test
    fun leOArquivoGeradoNoBuild() {
        val hashes = listOf("b.com", "a.com").map { HashedDomains.hash(it) }.sorted()
        val bytes = ByteBuffer.allocate(hashes.size * 8).apply { hashes.forEach { putLong(it) } }.array()
        val set = HashedDomains.read(bytes)
        assertEquals(2, set.size)
        assertTrue("a.com" in set)
        assertFalse("c.com" in set)
    }
}
