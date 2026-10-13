package com.pushlife.app.core

import java.time.LocalTime
import kotlin.math.roundToInt

/** Com que frequência a pessoa treina hoje; define a meta diária inicial de flexões. */
enum class ExerciseFrequency(val dailyGoal: Int) {
    NEVER(20),
    ONE_TO_THREE(30),
    THREE_TO_FIVE(50),
    DAILY(80),
}

/** Objetivos que a pessoa pode escolher nas boas-vindas (até 3). */
enum class Goal { LESS_PHONE, NIGHT_SCROLL, DISCIPLINE, STUDY, WORK, FITNESS }

data class Profile(
    val onboardingDone: Boolean = false,
    val name: String = "",
    val goals: Set<Goal> = emptySet(),
    val phoneHours: Float = 5f,
    val targetHours: Float = 3f,
    val exerciseFrequency: ExerciseFrequency? = null,
    val dailyGoal: Int = ExerciseFrequency.ONE_TO_THREE.dailyGoal,
    val reminder: LocalTime? = null,
    /** Índice da cor do avatar. */
    val avatarColor: Int = 0,
    val notifyStreak: Boolean = true,
    val notifyWeekly: Boolean = true,
    val notifyLowTime: Boolean = true,
    // Respostas das boas-vindas (para personalizar o app no futuro).
    val distractions: Set<DistractionApp> = emptySet(),
    val quitReasons: Set<QuitReason> = emptySet(),
    val triedMethods: Set<TriedMethod> = emptySet(),
)

/** A conta mostrada nas boas-vindas: horas por dia × 365 ÷ 24 = dias inteiros por ano. */
object Projection {
    fun daysPerYear(hoursPerDay: Float): Int = (hoursPerDay.coerceAtLeast(0f) * 365f / 24f).roundToInt()

    fun daysRecovered(currentHours: Float, targetHours: Float): Int = daysPerYear(currentHours - targetHours)

    /** Meta sugerida: 2 h a menos que hoje, nunca abaixo de meia hora. */
    fun suggestedTarget(currentHours: Float): Float = (currentHours - 2f).coerceAtLeast(0.5f)
}
