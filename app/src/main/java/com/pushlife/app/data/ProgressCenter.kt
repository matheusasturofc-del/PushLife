package com.pushlife.app.data

import com.pushlife.app.AppGraph
import com.pushlife.app.core.progress.AdaptiveGoal
import com.pushlife.app.core.progress.Stats
import com.pushlife.app.core.progress.WeeklyChallenge
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Junta saldo, perfil e progresso para as regras da gamificação. */
object ProgressCenter {
    fun stats(today: LocalDate = LocalDate.now()): Stats {
        val progress = AppGraph.progress.state.value
        return Stats.from(AppGraph.bank.state.value, progress.goalMetDays, progress.claimedWeeks.size, today)
    }

    /** Depois de cada exercício: registra o dia se a meta foi batida. */
    fun afterExercise(today: LocalDate = LocalDate.now()) {
        if (AppGraph.bank.state.value.effortMinutesOn(today) >= AppGraph.profile.state.value.dailyGoal) {
            AppGraph.progress.markGoalMet(today)
        }
    }

    /** Resgata o prêmio do desafio da semana, se concluído e ainda não resgatado. */
    fun claimChallenge(today: LocalDate = LocalDate.now()): Boolean {
        val weekStart = WeeklyChallenge.weekStart(today)
        val challenge = WeeklyChallenge.forWeek(weekStart)
        val claimed = weekStart in AppGraph.progress.state.value.claimedWeeks
        if (claimed || challenge.progress(AppGraph.bank.state.value, weekStart) < challenge.target) return false
        AppGraph.bank.addBonus(WeeklyChallenge.REWARD_SECONDS)
        AppGraph.progress.markClaimed(WeeklyChallenge.weekStart(today))
        return true
    }

    /**
     * Meta que evolui: uma vez a cada 7 dias olha a semana anterior e ajusta a meta diária.
     * @return a nova meta, se mudou.
     */
    fun adjustGoalIfDue(today: LocalDate = LocalDate.now()): Int? {
        val last = AppGraph.progress.state.value.lastGoalAdjust
        if (last == null) {
            AppGraph.progress.markGoalAdjusted(today)
            return null
        }
        if (ChronoUnit.DAYS.between(last, today) < 7) return null
        val bank = AppGraph.bank.state.value
        val week = (1L..7L).map { bank.effortMinutesOn(today.minusDays(it)) }
        val profile = AppGraph.profile.state.value
        val next = AdaptiveGoal.adjust(profile.dailyGoal, week)
        AppGraph.progress.markGoalAdjusted(today)
        if (next == profile.dailyGoal) return null
        AppGraph.profile.save(profile.copy(dailyGoal = next))
        return next
    }
}
