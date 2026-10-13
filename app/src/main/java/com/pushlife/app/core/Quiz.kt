package com.pushlife.app.core

import kotlin.math.roundToInt

/** Apps (ou tipos de app) que a pessoa diz que mais tomam seu tempo. */
enum class DistractionApp { TIKTOK, YOUTUBE, INSTAGRAM, GAMES, X, REDDIT, DISCORD, SHOPPING, TWITCH, STREAMING, SNAPCHAT, KWAI }

/** O que dificulta largar o celular. */
enum class QuitReason { FOMO, ADDICTIVE_DESIGN, AUTOMATIC, BOREDOM, PROCRASTINATION, STRESS, COMMUNITY, COMPARISON, WORK, TOO_EASY }

/** O que a pessoa já tentou antes. */
enum class TriedMethod { NOTHING, LIMITERS, UNINSTALL, BROWSER_ONLY, DETOX, GRAYSCALE, MINDSET, OUT_OF_REACH, DUMB_PHONE, ROUTINE, NFC_TAG, MINIMAL_LAUNCHER }

/**
 * Índice de dependência das boas-vindas: uma estimativa simples a partir das respostas,
 * não um diagnóstico. Horas no celular pesam 60 pontos (12 h ou mais = 60), quantos apps
 * puxam a atenção pesam 20 e quantos motivos dificultam parar pesam 20.
 */
object Quiz {
    private const val MAX_HOURS = 12f

    fun dependenceScore(hoursPerDay: Float, apps: Int, reasons: Int): Int =
        (hoursPart(hoursPerDay) + apps.coerceIn(0, 3) / 3f * 20 + reasons.coerceIn(0, 3) / 3f * 20).roundToInt()

    /** O mesmo índice com as horas da meta (sem os apps e motivos, que o app ajuda a resolver). */
    fun goalScore(targetHours: Float): Int = hoursPart(targetHours).roundToInt()

    private fun hoursPart(hours: Float) = hours.coerceIn(0f, MAX_HOURS) / MAX_HOURS * 60

    /** Anos inteiros (24 h por dia) olhando o celular se o hábito durar [years] anos (padrão: dos 15 aos 85). */
    fun lifeYears(hoursPerDay: Float, years: Int = 70): Int = (hoursPerDay.coerceAtLeast(0f) / 24f * years).roundToInt()

    fun yearsRecovered(currentHours: Float, targetHours: Float, years: Int = 70): Int =
        lifeYears((currentHours - targetHours).coerceAtLeast(0f), years)
}
