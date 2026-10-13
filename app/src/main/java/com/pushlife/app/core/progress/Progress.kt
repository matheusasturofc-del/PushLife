package com.pushlife.app.core.progress

import com.pushlife.app.core.BankState
import com.pushlife.app.core.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Dias seguidos com algum exercício. */
object Streaks {
    /** Sequência atual: termina hoje, ou ontem se hoje ainda não treinou. */
    fun current(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minusDays(1)
        var n = 0
        while (day in activeDays) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    fun best(activeDays: Set<LocalDate>): Int {
        var best = 0
        for (day in activeDays) {
            if (day.minusDays(1) in activeDays) continue // só começa a contar no 1º dia de cada sequência
            var n = 0
            var d = day
            while (d in activeDays) {
                n++
                d = d.plusDays(1)
            }
            best = maxOf(best, n)
        }
        return best
    }

    /** Sequência em risco: havia sequência até ontem e hoje ainda não treinou. */
    fun atRisk(activeDays: Set<LocalDate>, today: LocalDate): Boolean =
        today !in activeDays && today.minusDays(1) in activeDays
}

/** Níveis: subir do nível L para o L+1 custa 50 × L de XP. */
object Levels {
    fun xpToReach(level: Int): Int = 25 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        var level = 1
        while (xpToReach(level + 1) <= xp) level++
        return level
    }

    /** 0..1 dentro do nível atual. */
    fun progressInLevel(xp: Int): Float {
        val level = levelFor(xp)
        val start = xpToReach(level)
        return (xp - start).toFloat() / (xpToReach(level + 1) - start)
    }

    /** Título do nível (índice para os textos de cada idioma). */
    fun rank(level: Int): Rank = when {
        level >= 17 -> Rank.LEGEND
        level >= 12 -> Rank.BEAST
        level >= 8 -> Rank.ATHLETE
        level >= 5 -> Rank.FIT
        level >= 3 -> Rank.WARMING_UP
        else -> Rank.ROOKIE
    }

    enum class Rank { ROOKIE, WARMING_UP, FIT, ATHLETE, BEAST, LEGEND }
}

/** Calorias aproximadas por unidade (média para um adulto de ~70 kg; é só uma estimativa). */
object Calories {
    fun perUnit(exercise: Exercise): Double = when (exercise) {
        Exercise.PUSHUP -> 0.4
        Exercise.SQUAT -> 0.35
        Exercise.SITUP -> 0.25
        Exercise.JUMPING_JACK -> 0.2
        Exercise.PLANK -> 0.08 // por segundo
        Exercise.PULLUP -> 1.0
        Exercise.BURPEE -> 1.0
        Exercise.WALK -> 0.04 // por passo
    }

    fun of(counts: Map<Exercise, Int>): Int = counts.entries.sumOf { perUnit(it.key) * it.value }.roundToInt()
}

/** Números usados pelas conquistas, níveis e pela tela de progresso. */
data class Stats(
    val totals: Map<Exercise, Int>,
    val totalMinutes: Int,
    val activeDays: Set<LocalDate>,
    val currentStreak: Int,
    val bestStreak: Int,
    val goalDays: Int,
    val bestDayPushups: Int,
    val bestDayPlankSeconds: Int,
    val bestDaySteps: Int,
    val challengesDone: Int,
) {
    val xp: Int get() = totalMinutes + GOAL_DAY_XP * goalDays + CHALLENGE_XP * challengesDone
    val level: Int get() = Levels.levelFor(xp)
    val calories: Int get() = Calories.of(totals)

    companion object {
        const val GOAL_DAY_XP = 10
        const val CHALLENGE_XP = 50

        fun from(bank: BankState, goalMetDays: Set<LocalDate>, challengesDone: Int, today: LocalDate): Stats {
            val totals = mutableMapOf<Exercise, Int>()
            bank.countsByDay.values.forEach { day -> day.forEach { (ex, n) -> totals[ex] = (totals[ex] ?: 0) + n } }
            val active = bank.effortByDay.filterValues { it > 0 }.keys
            fun bestDay(ex: Exercise) = bank.countsByDay.values.maxOfOrNull { it[ex] ?: 0 } ?: 0
            return Stats(
                totals = totals,
                totalMinutes = (bank.effortByDay.values.sum() / 60).toInt(),
                activeDays = active,
                currentStreak = Streaks.current(active, today),
                bestStreak = Streaks.best(active),
                goalDays = goalMetDays.size,
                bestDayPushups = bestDay(Exercise.PUSHUP),
                bestDayPlankSeconds = bestDay(Exercise.PLANK),
                bestDaySteps = bestDay(Exercise.WALK),
                challengesDone = challengesDone,
            )
        }
    }
}

/** Conquistas: cada uma é uma condição sobre as [Stats]. */
enum class Achievement(val isUnlocked: (Stats) -> Boolean) {
    FIRST_WORKOUT({ it.totalMinutes > 0 }),
    PUSHUPS_100({ (it.totals[Exercise.PUSHUP] ?: 0) >= 100 }),
    PUSHUPS_1000({ (it.totals[Exercise.PUSHUP] ?: 0) >= 1000 }),
    DAY_50_PUSHUPS({ it.bestDayPushups >= 50 }),
    STREAK_3({ it.bestStreak >= 3 }),
    STREAK_7({ it.bestStreak >= 7 }),
    STREAK_30({ it.bestStreak >= 30 }),
    GOAL_7_DAYS({ it.goalDays >= 7 }),
    ALL_EXERCISES({ it.totals.filterValues { n -> n > 0 }.size == Exercise.entries.size }),
    PLANK_60({ it.bestDayPlankSeconds >= 60 }),
    STEPS_10K({ it.bestDaySteps >= 10_000 }),
    LEVEL_5({ it.level >= 5 }),
    LEVEL_10({ it.level >= 10 }),
    HOURS_10({ it.totalMinutes >= 600 }),
    ;

    companion object {
        fun unlocked(stats: Stats): Set<Achievement> = entries.filter { it.isUnlocked(stats) }.toSet()
    }
}

/** Um desafio por semana (segunda a domingo), que se repete em rodízio. */
enum class WeeklyChallenge(val target: Int) {
    PUSHUPS_300(300),
    ACTIVE_5_DAYS(5),
    MINUTES_180(180),
    SQUATS_200(200),
    PLANK_600(600),
    STEPS_50K(50_000),
    ;

    /** Quanto já foi feito na semana que começa em [weekStart]. */
    fun progress(bank: BankState, weekStart: LocalDate): Int {
        val days = (0L..6L).map { weekStart.plusDays(it) }
        fun sum(ex: Exercise) = days.sumOf { bank.countOn(it, ex) }
        return when (this) {
            PUSHUPS_300 -> sum(Exercise.PUSHUP)
            ACTIVE_5_DAYS -> days.count { (bank.effortByDay[it] ?: 0) > 0 }
            MINUTES_180 -> days.sumOf { bank.effortMinutesOn(it) }
            SQUATS_200 -> sum(Exercise.SQUAT)
            PLANK_600 -> sum(Exercise.PLANK)
            STEPS_50K -> sum(Exercise.WALK)
        }
    }

    companion object {
        const val REWARD_SECONDS = 30L * 60

        fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun forWeek(weekStart: LocalDate): WeeklyChallenge {
            val week = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + weekStart.get(IsoFields.WEEK_BASED_YEAR)
            return entries[week % entries.size]
        }
    }
}

/**
 * Meta que evolui: olha a última semana. Bateu a meta em 5 dias ou mais, sobe 10%;
 * em 1 dia ou menos, desce 10%. Sempre em múltiplos de 5, entre 10 e 300.
 */
object AdaptiveGoal {
    fun adjust(currentGoal: Int, lastWeekMinutes: List<Int>): Int {
        val met = lastWeekMinutes.count { it >= currentGoal }
        val factor = when {
            met >= 5 -> 1.1
            met <= 1 -> 0.9
            else -> return currentGoal
        }
        val next = ((currentGoal * factor / 5).roundToInt() * 5).coerceIn(10, 300)
        return when {
            factor > 1 && next == currentGoal -> (currentGoal + 5).coerceAtMost(300)
            factor < 1 && next == currentGoal -> (currentGoal - 5).coerceAtLeast(10)
            else -> next
        }
    }
}
