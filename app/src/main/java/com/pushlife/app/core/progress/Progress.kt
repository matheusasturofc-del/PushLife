package com.pushlife.app.core.progress

import com.pushlife.app.core.BankState
import com.pushlife.app.core.Exercise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// dias seguidos treinando
object Streaks {
    // termina hoje, ou ontem se hoje ainda não treinou
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

    // treinou até ontem e hoje ainda nada
    fun atRisk(activeDays: Set<LocalDate>, today: LocalDate): Boolean =
        today !in activeDays && today.minusDays(1) in activeDays
}

// nível L -> L+1 custa 50 × L de XP
object Levels {
    fun xpToReach(level: Int): Int = 25 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        var level = 1
        while (xpToReach(level + 1) <= xp) level++
        return level
    }

    // progresso dentro do nível (0..1)
    fun progressInLevel(xp: Int): Float {
        val level = levelFor(xp)
        val start = xpToReach(level)
        return (xp - start).toFloat() / (xpToReach(level + 1) - start)
    }

    // índice do título pros textos de cada idioma
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

// kcal por unidade, chute pra adulto de ~70 kg
object Calories {
    fun perUnit(exercise: Exercise): Double = when (exercise) {
        Exercise.PUSHUP -> 0.4
        Exercise.SQUAT -> 0.35
        Exercise.LUNGE -> 0.4
        Exercise.SITUP -> 0.25
        Exercise.JUMPING_JACK -> 0.2
        Exercise.PLANK -> 0.08 // por segundo
        Exercise.WALL_SIT -> 0.07 // por segundo
        Exercise.PULLUP -> 1.0
        Exercise.BURPEE -> 1.0
        Exercise.WALK -> 0.04 // por passo
    }

    fun of(counts: Map<Exercise, Int>): Int = counts.entries.sumOf { perUnit(it.key) * it.value }.roundToInt()
}

// números pras conquistas, níveis e tela de progresso
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

// conquistas, cada uma checa as Stats
// grupos das conquistas (a tela mostra cada um como uma seção)
enum class AchievementGroup { START, STRENGTH, CONSISTENCY, MILESTONES }

// conquista: quanto já tem (value) até o alvo; desbloqueia quando chega
enum class Achievement(val group: AchievementGroup, val target: Int, val value: (Stats) -> Int) {
    FIRST_WORKOUT(AchievementGroup.START, 1, { minOf(it.totalMinutes, 1) }),
    ALL_EXERCISES(AchievementGroup.START, Exercise.entries.size, { it.totals.filterValues { n -> n > 0 }.size }),
    PUSHUPS_100(AchievementGroup.STRENGTH, 100, { it.totals[Exercise.PUSHUP] ?: 0 }),
    PUSHUPS_1000(AchievementGroup.STRENGTH, 1000, { it.totals[Exercise.PUSHUP] ?: 0 }),
    PUSHUPS_5000(AchievementGroup.STRENGTH, 5000, { it.totals[Exercise.PUSHUP] ?: 0 }),
    DAY_50_PUSHUPS(AchievementGroup.STRENGTH, 50, { it.bestDayPushups }),
    PLANK_60(AchievementGroup.STRENGTH, 60, { it.bestDayPlankSeconds }),
    STEPS_10K(AchievementGroup.STRENGTH, 10_000, { it.bestDaySteps }),
    STREAK_3(AchievementGroup.CONSISTENCY, 3, { it.bestStreak }),
    STREAK_7(AchievementGroup.CONSISTENCY, 7, { it.bestStreak }),
    STREAK_14(AchievementGroup.CONSISTENCY, 14, { it.bestStreak }),
    STREAK_30(AchievementGroup.CONSISTENCY, 30, { it.bestStreak }),
    GOAL_7_DAYS(AchievementGroup.CONSISTENCY, 7, { it.goalDays }),
    GOAL_30_DAYS(AchievementGroup.CONSISTENCY, 30, { it.goalDays }),
    LEVEL_5(AchievementGroup.MILESTONES, 5, { it.level }),
    LEVEL_10(AchievementGroup.MILESTONES, 10, { it.level }),
    LEVEL_20(AchievementGroup.MILESTONES, 20, { it.level }),
    HOURS_10(AchievementGroup.MILESTONES, 600, { it.totalMinutes }),
    HOURS_50(AchievementGroup.MILESTONES, 3000, { it.totalMinutes }),
    ;

    fun isUnlocked(stats: Stats): Boolean = value(stats) >= target

    // 0..1 pra barra
    fun progress(stats: Stats): Float = (value(stats).toFloat() / target).coerceIn(0f, 1f)

    companion object {
        fun unlocked(stats: Stats): Set<Achievement> = entries.filter { it.isUnlocked(stats) }.toSet()
    }
}

// desafio da semana (seg a dom), em rodízio
enum class WeeklyChallenge(val target: Int) {
    PUSHUPS_300(300),
    ACTIVE_5_DAYS(5),
    MINUTES_180(180),
    SQUATS_200(200),
    PLANK_600(600),
    STEPS_50K(50_000),
    ;

    // quanto já fez na semana
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

// meta automática: bateu 5+ dias na semana sobe 10%, 1 ou menos desce 10%
// sempre múltiplo de 5, entre 10 e 300
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
