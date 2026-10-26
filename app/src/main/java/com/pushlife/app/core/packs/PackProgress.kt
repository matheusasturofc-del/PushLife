package com.pushlife.app.core.packs

import com.pushlife.app.core.ExerciseFrequency
import com.pushlife.app.core.Goal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// como foi o treino: muda o nível do próximo
enum class Feedback(val code: Char, val levelDelta: Float) {
    EASY('E', 1f),
    OK('O', 0.5f),
    HARD('H', -0.5f),
}

data class Session(val date: LocalDate, val feedback: Feedback)

// teste de evolução: score já ajustado pela variação (ex.: 10 flexões no joelho = 7,5)
data class Assessment(val date: LocalDate, val score: Float)

data class PackProgress(
    val level: Float = PackRules.MIN_LEVEL,
    val startedOn: LocalDate? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val sessions: List<Session> = emptyList(),
    val assessments: List<Assessment> = emptyList(),
    // semanas do programa que já deram bônus
    val claimedWeeks: Set<Int> = emptySet(),
) {
    val started: Boolean get() = startedOn != null

    // semana do programa (1, 2, …); 0 se não começou
    fun week(today: LocalDate): Int =
        startedOn?.let { (ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 7 + 1).toInt() } ?: 0

    fun doneOn(day: LocalDate): Boolean = sessions.any { it.date == day }

    fun sessionsInWeek(week: Int): Int {
        val start = startedOn ?: return 0
        val from = start.plusDays((week - 1) * 7L)
        val to = from.plusDays(7)
        return sessions.map { it.date }.distinct().count { !it.isBefore(from) && it.isBefore(to) }
    }

    fun scheduledOn(day: LocalDate): Boolean = started && day.dayOfWeek in days

    fun testDue(today: LocalDate): Boolean {
        if (!started) return false
        val last = assessments.maxByOrNull { it.date } ?: return true
        return ChronoUnit.DAYS.between(last.date, today) >= PackRules.TEST_EVERY_DAYS
    }

    // quanto melhorou desde o primeiro teste, em %; null com menos de 2 testes
    fun evolutionPercent(): Int? {
        if (assessments.size < 2) return null
        val sorted = assessments.sortedBy { it.date }
        val first = sorted.first().score
        if (first <= 0f) return null
        return ((sorted.last().score / first - 1f) * 100f).roundToInt()
    }
}

// resultado de terminar um treino: novo progresso e quanto ganhou de bônus
data class Finish(val progress: PackProgress, val workoutBonus: Long, val weekBonus: Long)

object PackPlanner {
    // dias padrão pra quantidade de treinos na semana (sempre com folga entre os de força)
    fun defaultDays(perWeek: Int): Set<DayOfWeek> = when (perWeek) {
        1 -> setOf(DayOfWeek.WEDNESDAY)
        2 -> setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        3 -> setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        4 -> setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        5 -> setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        6 -> DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY
        else -> DayOfWeek.entries.toSet()
    }

    // nível de partida de acordo com o quanto a pessoa já treina
    fun startLevel(pack: Pack, frequency: ExerciseFrequency?): Float {
        if (pack.category == PackCategory.STRETCH || pack.id == PackId.CHALLENGE_10) return PackRules.MIN_LEVEL
        return when (frequency) {
            null, ExerciseFrequency.NEVER -> 1f
            ExerciseFrequency.ONE_TO_THREE -> 3f
            ExerciseFrequency.THREE_TO_FIVE -> 5f
            ExerciseFrequency.DAILY -> 7f
        }
    }

    fun start(pack: Pack, frequency: ExerciseFrequency?, today: LocalDate, days: Set<DayOfWeek>? = null, previous: PackProgress? = null) =
        PackProgress(
            // recomeçar mantém o nível que a pessoa conquistou
            level = previous?.takeIf { it.started }?.level ?: startLevel(pack, frequency),
            startedOn = today,
            days = days ?: previous?.days?.takeIf { it.isNotEmpty() } ?: defaultDays(pack.daysPerWeek),
            assessments = previous?.assessments.orEmpty(),
        )

    // terminou o treino: ajusta o nível, marca o dia e vê se ganhou bônus
    // bônus do treino só 1 vez por dia por pack; bônus da semana quando bate os dias combinados
    fun finish(pack: Pack, progress: PackProgress, feedback: Feedback, today: LocalDate): Finish {
        val firstToday = !progress.doneOn(today)
        val level = (progress.level + feedback.levelDelta).coerceIn(PackRules.MIN_LEVEL, PackRules.MAX_LEVEL)
        var next = progress.copy(level = level, sessions = progress.sessions + Session(today, feedback))
        val week = next.week(today)
        val target = next.days.size.coerceAtLeast(1).coerceAtMost(pack.daysPerWeek)
        var weekBonus = 0L
        if (week in 1..pack.weeks && week !in next.claimedWeeks && next.sessionsInWeek(week) >= target) {
            next = next.copy(claimedWeeks = next.claimedWeeks + week)
            weekBonus = PackRules.WEEK_BONUS
        }
        return Finish(next, if (firstToday) pack.bonusSeconds else 0L, weekBonus)
    }

    // score do teste: repetições × dificuldade da variação, ou segundos, ou alcance
    fun score(pack: Pack, level: Float, result: Int): Float = when (pack.test.type) {
        TestType.MAX_REPS, TestType.REPS_IN_60 -> result * pack.test.slot.rungAt(level).move.difficulty
        TestType.MAX_HOLD, TestType.REACH -> result.toFloat()
    }

    fun recordTest(pack: Pack, progress: PackProgress, result: Int, today: LocalDate): PackProgress {
        val score = score(pack, progress.level, result)
        return progress.copy(assessments = progress.assessments.filterNot { it.date == today } + Assessment(today, score))
    }

    // packs pra hoje: os começados, com hoje na agenda e ainda não feitos
    fun dueToday(all: Map<PackId, PackProgress>, today: LocalDate): List<PackId> =
        all.filter { (id, p) -> p.scheduledOn(today) && !p.doneOn(today) && p.week(today) <= PackCatalog.get(id).weeks }.keys
            .sortedBy { it.ordinal }

    fun finishedProgram(pack: Pack, progress: PackProgress, today: LocalDate): Boolean = progress.week(today) > pack.weeks

    // sugestão da tela inicial: um de força pelo quanto já treina e um alongamento pelos objetivos
    fun suggest(frequency: ExerciseFrequency?, goals: Set<Goal>): List<PackId> {
        val strength = when (frequency) {
            null, ExerciseFrequency.NEVER -> PackId.CHALLENGE_10
            ExerciseFrequency.ONE_TO_THREE -> PackId.FULL
            ExerciseFrequency.THREE_TO_FIVE -> PackId.ARMS
            ExerciseFrequency.DAILY -> PackId.CHALLENGE_100
        }
        val stretch = when {
            Goal.NIGHT_SCROLL in goals -> PackId.STRETCH_NIGHT
            goals.any { it == Goal.LESS_PHONE || it == Goal.STUDY || it == Goal.WORK } -> PackId.STRETCH_NECK
            else -> PackId.STRETCH_MORNING
        }
        return listOf(strength, stretch)
    }
}

// formato guardado nas prefs (uma linha por pack):
// "lv=5.5|st=2026-10-20|d=1,3,5|s=2026-10-20:E,2026-10-22:O|a=2026-10-20:12.0|w=1,2"
object PackCodec {
    fun encode(p: PackProgress): String = listOf(
        "lv=${p.level}",
        "st=${p.startedOn ?: ""}",
        "d=" + p.days.sortedBy { it.value }.joinToString(",") { it.value.toString() },
        "s=" + p.sessions.joinToString(",") { "${it.date}:${it.feedback.code}" },
        "a=" + p.assessments.joinToString(",") { "${it.date}:${it.score}" },
        "w=" + p.claimedWeeks.sorted().joinToString(","),
    ).joinToString("|")

    fun decode(text: String): PackProgress {
        val f = text.split('|').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        fun list(key: String) = f[key].orEmpty().split(',').filter { it.isNotBlank() }
        fun date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
        return PackProgress(
            level = f["lv"]?.toFloatOrNull()?.coerceIn(PackRules.MIN_LEVEL, PackRules.MAX_LEVEL) ?: PackRules.MIN_LEVEL,
            startedOn = f["st"]?.let(::date),
            days = list("d").mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSet(),
            sessions = list("s").mapNotNull { s ->
                val (d, c) = s.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val fb = Feedback.entries.firstOrNull { it.code == c.firstOrNull() } ?: return@mapNotNull null
                date(d)?.let { Session(it, fb) }
            },
            assessments = list("a").mapNotNull { s ->
                val (d, v) = s.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                val score = v.toFloatOrNull() ?: return@mapNotNull null
                date(d)?.let { Assessment(it, score) }
            },
            claimedWeeks = list("w").mapNotNull { it.toIntOrNull() }.toSet(),
        )
    }
}
