package com.pushlife.app.core.progress

import com.pushlife.app.core.BankState
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.TimeBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 13)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test
    fun sequenciaAtualContaAteHojeOuOntem() {
        assertEquals(3, Streaks.current(days(0, 1, 2, 4), today))
        assertEquals(2, Streaks.current(days(1, 2, 5), today)) // hoje ainda não treinou
        assertEquals(0, Streaks.current(days(2, 3), today))
    }

    @Test
    fun recordeAchaAMaiorSequencia() {
        assertEquals(4, Streaks.best(days(0, 1, 5, 6, 7, 8, 10)))
        assertEquals(0, Streaks.best(emptySet()))
    }

    @Test
    fun emRiscoQuandoTreinouOntemMasNaoHoje() {
        assertTrue(Streaks.atRisk(days(1, 2), today))
        assertFalse(Streaks.atRisk(days(0, 1), today))
        assertFalse(Streaks.atRisk(days(3), today))
    }
}

class LevelsTest {
    @Test
    fun niveisFicamMaisCarosAosPoucos() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(49))
        assertEquals(2, Levels.levelFor(50))
        assertEquals(3, Levels.levelFor(150))
        assertEquals(10, Levels.levelFor(Levels.xpToReach(10)))
        assertEquals(0.5f, Levels.progressInLevel(100), 0.001f) // nível 2: de 50 a 150
    }

    @Test
    fun titulos() {
        assertEquals(Levels.Rank.ROOKIE, Levels.rank(1))
        assertEquals(Levels.Rank.FIT, Levels.rank(5))
        assertEquals(Levels.Rank.LEGEND, Levels.rank(20))
    }
}

class StatsAndAchievementsTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun bankWith(vararg entries: Triple<Long, Exercise, Int>): BankState =
        entries.fold(BankState()) { s, (back, ex, n) -> TimeBank.addExercise(s, ex, n, today.minusDays(back)).state }

    @Test
    fun estatisticasJuntamTodosOsDias() {
        val bank = bankWith(Triple(0, Exercise.PUSHUP, 30), Triple(1, Exercise.PUSHUP, 20), Triple(1, Exercise.SQUAT, 10))
        val s = Stats.from(bank, goalMetDays = setOf(today), challengesDone = 0, today = today)
        assertEquals(50, s.totals[Exercise.PUSHUP])
        assertEquals(55, s.totalMinutes) // 50 flexões + 10 agachamentos × 30 s
        assertEquals(2, s.currentStreak)
        assertEquals(55 + 10, s.xp)
        assertEquals(30, s.bestDayPushups)
        assertEquals(24, s.calories) // 50 × 0,4 + 10 × 0,35 = 23,5
    }

    @Test
    fun conquistasDesbloqueiam() {
        val bank = bankWith(Triple(0, Exercise.PUSHUP, 60), Triple(1, Exercise.PUSHUP, 50), Triple(2, Exercise.WALK, 12_000))
        val got = Achievement.unlocked(Stats.from(bank, emptySet(), 0, today))
        assertTrue(Achievement.FIRST_WORKOUT in got)
        assertTrue(Achievement.PUSHUPS_100 in got)
        assertTrue(Achievement.DAY_50_PUSHUPS in got)
        assertTrue(Achievement.STREAK_3 in got)
        assertTrue(Achievement.STEPS_10K in got)
        assertFalse(Achievement.STREAK_7 in got)
        assertFalse(Achievement.ALL_EXERCISES in got)
    }

    @Test
    fun semTreinoNenhumaConquista() {
        assertTrue(Achievement.unlocked(Stats.from(BankState(), emptySet(), 0, today)).isEmpty())
    }
}

class WeeklyChallengeTest {
    @Test
    fun semanaComecaNaSegunda() {
        assertEquals(LocalDate.of(2026, 10, 12), WeeklyChallenge.weekStart(LocalDate.of(2026, 10, 13))) // terça
        assertEquals(LocalDate.of(2026, 10, 12), WeeklyChallenge.weekStart(LocalDate.of(2026, 10, 18))) // domingo
    }

    @Test
    fun desafioMudaACadaSemanaESeRepete() {
        val w1 = LocalDate.of(2026, 10, 12)
        val sequence = (0L..5L).map { WeeklyChallenge.forWeek(w1.plusWeeks(it)) }
        assertEquals(WeeklyChallenge.entries.size, sequence.toSet().size)
        assertEquals(WeeklyChallenge.forWeek(w1), WeeklyChallenge.forWeek(w1))
    }

    @Test
    fun progressoSoContaASemana() {
        val monday = LocalDate.of(2026, 10, 12)
        var bank = BankState()
        bank = TimeBank.addExercise(bank, Exercise.PUSHUP, 100, monday).state
        bank = TimeBank.addExercise(bank, Exercise.PUSHUP, 50, monday.plusDays(6)).state
        bank = TimeBank.addExercise(bank, Exercise.PUSHUP, 999, monday.minusDays(1)).state // semana passada
        assertEquals(150, WeeklyChallenge.PUSHUPS_300.progress(bank, monday))
        assertEquals(2, WeeklyChallenge.ACTIVE_5_DAYS.progress(bank, monday))
    }
}

class AdaptiveGoalTest {
    @Test
    fun sobeQuandoBateQuaseTodoDia() {
        assertEquals(55, AdaptiveGoal.adjust(50, listOf(60, 50, 70, 55, 51, 0, 0)))
        assertEquals(35, AdaptiveGoal.adjust(30, List(7) { 40 })) // 33 arredonda para 35
    }

    @Test
    fun desceQuandoQuaseNaoBate() {
        assertEquals(45, AdaptiveGoal.adjust(50, listOf(10, 0, 0, 0, 0, 0, 60)))
        assertEquals(10, AdaptiveGoal.adjust(10, List(7) { 0 })) // nunca abaixo de 10
    }

    @Test
    fun ficaIgualNoMeioTermo() {
        assertEquals(50, AdaptiveGoal.adjust(50, listOf(50, 50, 50, 0, 0, 0, 0)))
    }

    @Test
    fun conquistasTemProgressoEGrupo() {
        val today = java.time.LocalDate.of(2026, 10, 13)
        var bank = com.pushlife.app.core.BankState()
        bank = com.pushlife.app.core.TimeBank.addExercise(bank, com.pushlife.app.core.Exercise.PUSHUP, 40, today).state
        val stats = Stats.from(bank, emptySet(), 0, today)
        assertEquals(0.4f, Achievement.PUSHUPS_100.progress(stats), 0.001f)
        assertEquals(40, Achievement.PUSHUPS_1000.value(stats))
        assertTrue(Achievement.FIRST_WORKOUT.isUnlocked(stats))
        // todo grupo tem conquista e toda conquista tem alvo
        AchievementGroup.entries.forEach { g -> assertTrue(Achievement.entries.any { it.group == g }) }
        assertTrue(Achievement.entries.all { it.target > 0 })
    }
}
