package com.pushlife.app.core.filter

import java.nio.ByteBuffer

// lista grande de sites, guardada como números (8 bytes cada, em ordem)
// ~1 milhão cabe em ~8 MB e a busca binária é na hora
class HashedDomains(private val sorted: LongArray) {
    val size get() = sorted.size

    operator fun contains(domain: String) = sorted.binarySearch(hash(domain)) >= 0

    companion object {
        // FNV-1a 64 bits, tem que bater com o do Gradle que monta a lista
        fun hash(domain: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (b in domain.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h
        }

        // arquivo = números em sequência (big-endian), já em ordem
        fun read(bytes: ByteArray): HashedDomains {
            val longs = LongArray(bytes.size / 8)
            ByteBuffer.wrap(bytes).asLongBuffer().get(longs)
            return HashedDomains(longs)
        }

        fun of(domains: Collection<String>) = HashedDomains(domains.map { hash(it) }.distinct().sorted().toLongArray())
    }
}
