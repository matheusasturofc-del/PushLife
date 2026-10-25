package com.pushlife.app.core

import java.text.Normalizer

// @: 3 a 20 caracteres, minúsculas sem acento, números, _ e ., começando com letra
// também é o nome do documento que impede @ repetido
object Username {
    const val MIN = 3
    const val MAX = 20

    enum class Problem { TOO_SHORT, BAD_START }

    private val accents = Regex("\\p{Mn}+")
    private val notAllowed = Regex("[^a-z0-9_.]")

    // limpa o que digitou: "@Matheus Ástur" → "matheusastur"
    fun clean(input: String): String =
        Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(accents, "")
            .replace(notAllowed, "")
            .take(MAX)

    // sugestão a partir do nome
    fun suggest(name: String): String = clean(name).trimStart { !it.isLetter() }

    // null = @ liberado
    fun problem(handle: String): Problem? = when {
        handle.length < MIN -> Problem.TOO_SHORT
        handle.first() !in 'a'..'z' -> Problem.BAD_START
        else -> null
    }
}
