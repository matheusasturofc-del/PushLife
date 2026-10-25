package com.pushlife.app.core.filter

import java.text.Normalizer
import java.time.LocalDate

/**
 * Desafio para desligar o filtro de sites adultos: reescrever um texto de 15 linhas em até 3 minutos.
 * A comparação ignora maiúsculas, acentos, pontuação e espaços extras; colar não vale.
 */
object Challenge {
    const val SECONDS = 180

    /** Mais que isso de uma vez só não é digitação (é colar). Sugestões do teclado inserem uma palavra. */
    const val MAX_JUMP = 20

    private val marks = Regex("\\p{Mn}+")
    private val notWord = Regex("[^a-z0-9]+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(notWord, " ")
            .trim()

    data class Progress(
        /** Quantos caracteres (já normalizados) batem com o começo do texto. */
        val correct: Int,
        val total: Int,
        /** false quando a pessoa digitou algo diferente do texto. */
        val onTrack: Boolean,
        val linesDone: Int,
        val done: Boolean,
    )

    fun progress(typed: String, lines: List<String>): Progress {
        val target = normalize(lines.joinToString(" "))
        val t = normalize(typed)
        val correct = t.zip(target).takeWhile { (a, b) -> a == b }.size
        val linesDone = (1..lines.size).count { n ->
            val upTo = normalize(lines.take(n).joinToString(" "))
            t.startsWith(upTo) && (t.length == upTo.length || t[upTo.length] == ' ')
        }
        return Progress(
            correct = correct,
            total = target.length,
            onTrack = target.startsWith(t),
            linesDone = linesDone,
            done = t == target,
        )
    }

    fun looksPasted(before: String, after: String): Boolean = after.length - before.length > MAX_JUMP

    // Uma tentativa por dia, pela data do celular: libera de novo à meia-noite.
    fun canTry(lastTry: LocalDate?, today: LocalDate): Boolean = lastTry == null || today.isAfter(lastTry)
}
