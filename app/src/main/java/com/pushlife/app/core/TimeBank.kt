package com.pushlife.app.core

import java.time.LocalDate

// regras do tempo de tela
object Rules {
    const val FREE_SECONDS_PER_DAY = 60L * 60 // padrão: 1 h grátis por dia (não acumula)
    const val MAX_EARNED_SECONDS = 2L * 60 * 60 // o saldo ganho acumula até 2 h

    // emergência: libera sem saldo, mas fica devendo exercício
    const val EMERGENCY_SECONDS = 5L * 60
    const val EMERGENCY_DEBT_PUSHUPS = 10
    const val EMERGENCY_DEBT_SECONDS = EMERGENCY_DEBT_PUSHUPS * 60L
    const val MAX_EMERGENCIES_PER_DAY = 3

    // grátis do dia = metade da meta de tela (meta 1 h → 30 min), de 5 em 5 min, mínimo 5 min
    fun freeSecondsFor(targetHours: Float): Long {
        val half = targetHours.coerceAtLeast(0f) * 3600f / 2f
        return (Math.round(half / 300f) * 300L).coerceAtLeast(300L)
    }
}

// saldo de tela: o grátis do dia sai primeiro e zera à meia-noite
// o ganho com exercício acumula até MAX_EARNED_SECONDS
data class BankState(
    val earnedSeconds: Long = 0,
    val freeUsedSeconds: Long = 0,
    val freeDay: LocalDate? = null,
    // quanto fez de cada exercício por dia (reps, segundos ou passos)
    val countsByDay: Map<LocalDate, Map<Exercise, Int>> = emptyMap(),
    // quanto o exercício de cada dia valeu, mesmo passando do limite
    val effortByDay: Map<LocalDate, Long> = emptyMap(),
    // dívida das emergências, paga antes de virar saldo
    val debtSeconds: Long = 0,
    val emergencyDay: LocalDate? = null,
    val emergenciesUsed: Int = 0,
    // até quando a emergência libera (ms, relógio do sistema)
    val emergencyUntilMs: Long = 0,
    // grátis por dia (metade da meta de tela da pessoa)
    val freeDailySeconds: Long = Rules.FREE_SECONDS_PER_DAY,
) {
    fun freeRemaining(today: LocalDate): Long =
        if (freeDay == today) (freeDailySeconds - freeUsedSeconds).coerceAtLeast(0) else freeDailySeconds

    fun available(today: LocalDate): Long = freeRemaining(today) + earnedSeconds

    fun countOn(day: LocalDate, exercise: Exercise): Int = countsByDay[day]?.get(exercise) ?: 0

    // minutos ganhos no dia (1 flexão = 1 min), é o que conta pra meta
    fun effortMinutesOn(day: LocalDate): Int = ((effortByDay[day] ?: 0) / 60).toInt()

    // dívida em flexões, arredonda pra cima
    val debtPushups: Int get() = ((debtSeconds + 59) / 60).toInt()

    fun emergenciesLeft(today: LocalDate): Int =
        Rules.MAX_EMERGENCIES_PER_DAY - if (emergencyDay == today) emergenciesUsed else 0
}

// resultado: novo estado, quanto entrou e quanto pagou da dívida
data class Credit(val state: BankState, val creditedSeconds: Long, val hitCap: Boolean, val debtPaidSeconds: Long = 0)

object TimeBank {
    fun addExercise(state: BankState, exercise: Exercise, amount: Int, today: LocalDate): Credit {
        require(amount >= 0)
        val value = exercise.valueOf(amount)
        val debtPaid = minOf(value, state.debtSeconds)
        val wanted = value - debtPaid
        val newEarned = (state.earnedSeconds + wanted).coerceAtMost(Rules.MAX_EARNED_SECONDS)
        val credited = (newEarned - state.earnedSeconds).coerceAtLeast(0)
        val dayCounts = state.countsByDay[today].orEmpty()
        val newState = state.copy(
            earnedSeconds = maxOf(newEarned, state.earnedSeconds),
            countsByDay = state.countsByDay + (today to dayCounts + (exercise to (dayCounts[exercise] ?: 0) + amount)),
            effortByDay = state.effortByDay + (today to (state.effortByDay[today] ?: 0) + value),
            debtSeconds = state.debtSeconds - debtPaid,
        )
        return Credit(newState, credited, hitCap = credited < wanted, debtPaidSeconds = debtPaid)
    }

    // prêmio (desafio semanal): entra no saldo, mas não conta como exercício
    fun addBonus(state: BankState, seconds: Long): Credit {
        val newEarned = (state.earnedSeconds + seconds).coerceAtMost(Rules.MAX_EARNED_SECONDS)
        val credited = (newEarned - state.earnedSeconds).coerceAtLeast(0)
        return Credit(state.copy(earnedSeconds = maxOf(newEarned, state.earnedSeconds)), credited, hitCap = credited < seconds)
    }

    // gasta tempo: primeiro o grátis, depois o ganho, nunca negativo
    fun spend(state: BankState, seconds: Long, today: LocalDate): BankState {
        require(seconds >= 0)
        val fromFree = minOf(seconds, state.freeRemaining(today))
        val fromEarned = minOf(seconds - fromFree, state.earnedSeconds)
        val usedToday = if (state.freeDay == today) state.freeUsedSeconds else 0
        return state.copy(
            freeDay = today,
            freeUsedSeconds = usedToday + fromFree,
            earnedSeconds = state.earnedSeconds - fromEarned,
        )
    }

    // usa uma emergência; null se acabaram as de hoje
    fun emergency(state: BankState, today: LocalDate, nowMs: Long): BankState? {
        if (state.emergenciesLeft(today) <= 0) return null
        return state.copy(
            debtSeconds = state.debtSeconds + Rules.EMERGENCY_DEBT_SECONDS,
            emergencyDay = today,
            emergenciesUsed = (if (state.emergencyDay == today) state.emergenciesUsed else 0) + 1,
            emergencyUntilMs = nowMs + Rules.EMERGENCY_SECONDS * 1000,
        )
    }
}
