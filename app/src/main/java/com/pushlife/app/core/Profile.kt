package com.pushlife.app.core

import java.time.LocalTime
import kotlin.math.roundToInt

// quanto treina hoje, define a meta inicial de flexões
enum class ExerciseFrequency(val dailyGoal: Int) {
    NEVER(20),
    ONE_TO_THREE(30),
    THREE_TO_FIVE(50),
    DAILY(80),
}

// objetivos das boas-vindas (até 3)
enum class Goal { LESS_PHONE, NIGHT_SCROLL, DISCIPLINE, STUDY, WORK, FITNESS }

data class Profile(
    val onboardingDone: Boolean = false,
    val name: String = "",
    // @ da conta, sem o "@" (vazio se não tem conta)
    val username: String = "",
    // convidado: "guest" + números, sem backup, não troca nome nem foto
    val guest: Boolean = false,
    val goals: Set<Goal> = emptySet(),
    val phoneHours: Float = 5f,
    val targetHours: Float = 3f,
    val exerciseFrequency: ExerciseFrequency? = null,
    val dailyGoal: Int = ExerciseFrequency.ONE_TO_THREE.dailyGoal,
    val reminder: LocalTime? = null,
    // cor do avatar (índice)
    val avatarColor: Int = 0,
    val notifyStreak: Boolean = true,
    val notifyWeekly: Boolean = true,
    val notifyLowTime: Boolean = true,
    // respostas das boas-vindas (pra personalizar depois)
    val distractions: Set<DistractionApp> = emptySet(),
    val quitReasons: Set<QuitReason> = emptySet(),
    val triedMethods: Set<TriedMethod> = emptySet(),
)

// conta das boas-vindas: horas/dia × 365 ÷ 24 = dias por ano
object Projection {
    fun daysPerYear(hoursPerDay: Float): Int = (hoursPerDay.coerceAtLeast(0f) * 365f / 24f).roundToInt()

    fun daysRecovered(currentHours: Float, targetHours: Float): Int = daysPerYear(currentHours - targetHours)

    // meta: 2 h a menos que hoje, mínimo meia hora
    fun suggestedTarget(currentHours: Float): Float = (currentHours - 2f).coerceAtLeast(0.5f)
}
