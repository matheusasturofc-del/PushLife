package com.pushlife.app.core.filter

import java.nio.ByteBuffer

// Lista grande de domínios guardada como números (8 bytes cada), ordenados.
// Quase 1 milhão de sites cabem em ~8 MB e a busca é binária: instantânea.
class HashedDomains(private val sorted: LongArray) {
    val size get() = sorted.size

    operator fun contains(domain: String) = sorted.binarySearch(hash(domain)) >= 0

    companion object {
        // FNV-1a de 64 bits. O mesmo cálculo roda no Gradle, ao montar a lista.
        fun hash(domain: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (b in domain.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h
        }

        // Arquivo: os números em sequência (big-endian), já ordenados.
        fun read(bytes: ByteArray): HashedDomains {
            val longs = LongArray(bytes.size / 8)
            ByteBuffer.wrap(bytes).asLongBuffer().get(longs)
            return HashedDomains(longs)
        }

        fun of(domains: Collection<String>) = HashedDomains(domains.map { hash(it) }.distinct().sorted().toLongArray())
    }
}
