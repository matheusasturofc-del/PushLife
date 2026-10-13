package com.pushlife.app.core

import java.time.LocalDate

/** Regras do tempo de tela combinadas no questionário. */
object Rules {
    const val SECONDS_PER_PUSHUP = 60L
    const val FREE_SECONDS_PER_DAY = 60L * 60 // 1 h grátis por dia (não acumula)
    const val MAX_EARNED_SECONDS = 2L * 60 * 60 // o saldo ganho acumula até 2 h

    // Emergência: libera os apps sem saldo, mas a pessoa fica devendo flexões.
    const val EMERGENCY_SECONDS = 5L * 60
    const val EMERGENCY_DEBT_REPS = 10
    const val MAX_EMERGENCIES_PER_DAY = 3
}

/**
 * Saldo de tempo de tela. O tempo grátis do dia é gasto primeiro e zera à meia-noite;
 * o tempo ganho com exercício acumula até [Rules.MAX_EARNED_SECONDS].
 */
data class BankState(
    val earnedSeconds: Long = 0,
    val freeUsedSeconds: Long = 0,
    val freeDay: LocalDate? = null,
    val repsByDay: Map<LocalDate, Int> = emptyMap(),
    val totalReps: Int = 0,
    /** Flexões devidas por emergências; são pagas antes de virar minutos. */
    val debtReps: Int = 0,
    val emergencyDay: LocalDate? = null,
    val emergenciesUsed: Int = 0,
    /** Até quando (relógio do sistema, em ms) a emergência atual libera os apps. */
    val emergencyUntilMs: Long = 0,
) {
    fun freeRemaining(today: LocalDate): Long =
        if (freeDay == today) (Rules.FREE_SECONDS_PER_DAY - freeUsedSeconds).coerceAtLeast(0) else Rules.FREE_SECONDS_PER_DAY

    fun available(today: LocalDate): Long = freeRemaining(today) + earnedSeconds

    fun repsOn(day: LocalDate): Int = repsByDay[day] ?: 0

    fun emergenciesLeft(today: LocalDate): Int =
        Rules.MAX_EMERGENCIES_PER_DAY - if (emergencyDay == today) emergenciesUsed else 0
}

/** Resultado de registrar repetições: o novo estado, quanto tempo entrou e quanto da dívida foi paga. */
data class Credit(val state: BankState, val creditedSeconds: Long, val hitCap: Boolean, val debtPaid: Int = 0)

object TimeBank {
    fun addReps(state: BankState, reps: Int, today: LocalDate, secondsPerRep: Long = Rules.SECONDS_PER_PUSHUP): Credit {
        require(reps >= 0)
        val debtPaid = minOf(reps, state.debtReps)
        val wanted = (reps - debtPaid) * secondsPerRep
        val newEarned = (state.earnedSeconds + wanted).coerceAtMost(Rules.MAX_EARNED_SECONDS)
        val credited = (newEarned - state.earnedSeconds).coerceAtLeast(0)
        val newState = state.copy(
            earnedSeconds = maxOf(newEarned, state.earnedSeconds),
            repsByDay = state.repsByDay + (today to state.repsOn(today) + reps),
            totalReps = state.totalReps + reps,
            debtReps = state.debtReps - debtPaid,
        )
        return Credit(newState, credited, hitCap = credited < wanted, debtPaid = debtPaid)
    }

    /** Gasta tempo de tela: primeiro o grátis do dia, depois o ganho. Não deixa ficar negativo. */
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

    /** Usa uma emergência, se ainda houver hoje. Retorna null quando acabaram. */
    fun emergency(state: BankState, today: LocalDate, nowMs: Long): BankState? {
        if (state.emergenciesLeft(today) <= 0) return null
        return state.copy(
            debtReps = state.debtReps + Rules.EMERGENCY_DEBT_REPS,
            emergencyDay = today,
            emergenciesUsed = (if (state.emergencyDay == today) state.emergenciesUsed else 0) + 1,
            emergencyUntilMs = nowMs + Rules.EMERGENCY_SECONDS * 1000,
        )
    }
}
