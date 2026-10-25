package com.pushlife.app.core

import kotlin.math.roundToInt

// apps que mais roubam o tempo da pessoa
enum class DistractionApp { TIKTOK, YOUTUBE, INSTAGRAM, GAMES, X, REDDIT, DISCORD, SHOPPING, TWITCH, STREAMING, SNAPCHAT, KWAI }

// o que dificulta largar o celular
enum class QuitReason { FOMO, ADDICTIVE_DESIGN, AUTOMATIC, BOREDOM, PROCRASTINATION, STRESS, COMMUNITY, COMPARISON, WORK, TOO_EASY }

// o que já tentou antes
enum class TriedMethod { NOTHING, LIMITERS, UNINSTALL, BROWSER_ONLY, DETOX, GRAYSCALE, MINDSET, OUT_OF_REACH, DUMB_PHONE, ROUTINE, NFC_TAG, MINIMAL_LAUNCHER }

// índice de dependência (só estimativa, não é diagnóstico)
// horas = 60 pts (12 h+ já é o máximo), apps = 20, motivos = 20
object Quiz {
    private const val MAX_HOURS = 12f

    fun dependenceScore(hoursPerDay: Float, apps: Int, reasons: Int): Int =
        (hoursPart(hoursPerDay) + apps.coerceIn(0, 3) / 3f * 20 + reasons.coerceIn(0, 3) / 3f * 20).roundToInt()

    // mesmo índice só com as horas da meta (apps e motivos o app ajuda a resolver)
    fun goalScore(targetHours: Float): Int = hoursPart(targetHours).roundToInt()

    private fun hoursPart(hours: Float) = hours.coerceIn(0f, MAX_HOURS) / MAX_HOURS * 60

    // anos inteiros (24h/dia) no celular se o hábito durar years anos (padrão: 15 aos 85)
    fun lifeYears(hoursPerDay: Float, years: Int = 70): Int = (hoursPerDay.coerceAtLeast(0f) / 24f * years).roundToInt()

    fun yearsRecovered(currentHours: Float, targetHours: Float, years: Int = 70): Int =
        lifeYears((currentHours - targetHours).coerceAtLeast(0f), years)
}
