package com.pushlife.app.core

import java.time.LocalDate

/** Regras do tempo de tela combinadas no questionário. */
object Rules {
    const val FREE_SECONDS_PER_DAY = 60L * 60 // 1 h grátis por dia (não acumula)
    const val MAX_EARNED_SECONDS = 2L * 60 * 60 // o saldo ganho acumula até 2 h

    // Emergência: libera os apps sem saldo, mas a pessoa fica devendo exercício.
    const val EMERGENCY_SECONDS = 5L * 60
    const val EMERGENCY_DEBT_PUSHUPS = 10
    const val EMERGENCY_DEBT_SECONDS = EMERGENCY_DEBT_PUSHUPS * 60L
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
    /** Quanto de cada exercício foi feito por dia (repetições, segundos de prancha ou passos). */
    val countsByDay: Map<LocalDate, Map<Exercise, Int>> = emptyMap(),
    /** Tempo de tela que os exercícios de cada dia valeram, mesmo acima do limite guardado. */
    val effortByDay: Map<LocalDate, Long> = emptyMap(),
    /** Tempo devido por emergências; é pago antes de o exercício virar saldo. */
    val debtSeconds: Long = 0,
    val emergencyDay: LocalDate? = null,
    val emergenciesUsed: Int = 0,
    /** Até quando (relógio do sistema, em ms) a emergência atual libera os apps. */
    val emergencyUntilMs: Long = 0,
) {
    fun freeRemaining(today: LocalDate): Long =
        if (freeDay == today) (Rules.FREE_SECONDS_PER_DAY - freeUsedSeconds).coerceAtLeast(0) else Rules.FREE_SECONDS_PER_DAY

    fun available(today: LocalDate): Long = freeRemaining(today) + earnedSeconds

    fun countOn(day: LocalDate, exercise: Exercise): Int = countsByDay[day]?.get(exercise) ?: 0

    /** Minutos ganhos com exercício no dia (1 flexão = 1 minuto): é a unidade da meta diária. */
    fun effortMinutesOn(day: LocalDate): Int = ((effortByDay[day] ?: 0) / 60).toInt()

    /** A dívida mostrada em flexões, arredondando para cima. */
    val debtPushups: Int get() = ((debtSeconds + 59) / 60).toInt()

    fun emergenciesLeft(today: LocalDate): Int =
        Rules.MAX_EMERGENCIES_PER_DAY - if (emergencyDay == today) emergenciesUsed else 0
}

/** Resultado de registrar exercício: o novo estado, quanto entrou no saldo e quanto pagou da dívida. */
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

    /** Prêmio (ex.: desafio semanal): entra no saldo respeitando o limite, sem contar como exercício. */
    fun addBonus(state: BankState, seconds: Long): Credit {
        val newEarned = (state.earnedSeconds + seconds).coerceAtMost(Rules.MAX_EARNED_SECONDS)
        val credited = (newEarned - state.earnedSeconds).coerceAtLeast(0)
        return Credit(state.copy(earnedSeconds = maxOf(newEarned, state.earnedSeconds)), credited, hitCap = credited < seconds)
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
            debtSeconds = state.debtSeconds + Rules.EMERGENCY_DEBT_SECONDS,
            emergencyDay = today,
            emergenciesUsed = (if (state.emergencyDay == today) state.emergenciesUsed else 0) + 1,
            emergencyUntilMs = nowMs + Rules.EMERGENCY_SECONDS * 1000,
        )
    }
}
