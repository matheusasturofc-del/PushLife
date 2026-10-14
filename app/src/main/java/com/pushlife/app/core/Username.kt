package com.pushlife.app.core

import java.text.Normalizer

/**
 * O @ de cada pessoa: 3 a 20 caracteres, letras minúsculas sem acento, números, "_" e ".",
 * começando com letra. É também o nome do documento que garante que ninguém repita o @.
 */
object Username {
    const val MIN = 3
    const val MAX = 20

    enum class Problem { TOO_SHORT, BAD_START }

    private val accents = Regex("\\p{Mn}+")
    private val notAllowed = Regex("[^a-z0-9_.]")

    /** Converte o que a pessoa digitou para um @ válido em formato: "@Matheus Ástur" → "matheusastur". */
    fun clean(input: String): String =
        Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            .replace(accents, "")
            .replace(notAllowed, "")
            .take(MAX)

    /** Sugestão a partir do nome, para a pessoa não começar do zero. */
    fun suggest(name: String): String = clean(name).trimStart { !it.isLetter() }

    /** null quando o @ (já limpo) pode ser usado. */
    fun problem(handle: String): Problem? = when {
        handle.length < MIN -> Problem.TOO_SHORT
        handle.first() !in 'a'..'z' -> Problem.BAD_START
        else -> null
    }
}
