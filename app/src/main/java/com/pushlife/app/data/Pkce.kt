package com.pushlife.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// PKCE do login: o segredo fica no app, só o hash dele vai na URL
object Pkce {
    private val random = SecureRandom()

    fun newVerifier(): String {
        val bytes = ByteArray(48).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
