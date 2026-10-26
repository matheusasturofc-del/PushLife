package com.pushlife.app.core.packs

import com.pushlife.app.core.ExerciseFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PacksTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val arms = PackCatalog.get(PackId.ARMS)

    @Test
    fun flexaoSobeDaParedeAteADiamante() {
        val slot = arms.main.first()
        assertEquals(Move.WALL_PUSHUP, slot.rungAt(1f).move)
        assertEquals(Move.INCLINE_PUSHUP, slot.rungAt(5f).move)
        assertEquals(Move.KNEE_PUSHUP, slot.rungAt(9.5f).move)
        assertEquals(Move.PUSHUP, slot.rungAt(13f).move)
        assertEquals(Move.DIAMOND_PUSHUP, slot.rungAt(20f).move)
        // dentro do degrau, +1 repetição por nível
        assertEquals(8, slot.rungAt(1f).amountAt(1f))
        assertEquals(10, slot.rungAt(3f).amountAt(3f))
        assertEquals(5, slot.rungAt(13f).amountAt(13f))
    }

    @Test
    fun treinoTemAquecimentoSeriesEAlongamento() {
        val steps = Workouts.build(arms, 1f)
        assertEquals(Phase.WARMUP, steps.first().phase)
        assertEquals(Phase.COOLDOWN, steps.last().phase)
        val main = steps.filter { it.phase == Phase.MAIN }
        assertEquals(3 + 3 + 2, main.size)
        assertEquals(0, main.last().restAfter)
        assertEquals(60, main.first().restAfter)
        assertTrue(Workouts.minutes(steps) in 8..25)
    }

    @Test
    fun circuitoRepeteAVolta() {
        val full = PackCatalog.get(PackId.FULL)
        val main = Workouts.build(full, 1f).filter { it.phase == Phase.MAIN }
        assertEquals(full.main.size * 3, main.size)
        assertEquals(Move.JUMPING_JACK, main[0].move)
        assertEquals(Move.JUMPING_JACK, main[full.main.size].move)
        assertEquals(2, main[full.main.size].set)
    }

    @Test
    fun todosOsPacksGeramTreino() {
        PackCatalog.all.forEach { pack ->
            listOf(1f, 10f, 20f).forEach { level ->
                val steps = Workouts.build(pack, level)
                assertTrue(pack.id.name, steps.isNotEmpty())
                assertTrue(pack.id.name, steps.all { it.amount > 0 })
            }
        }
    }

    @Test
    fun feedbackMudaONivel() {
        var p = PackPlanner.start(arms, ExerciseFrequency.NEVER, monday)
        assertEquals(1f, p.level)
        p = PackPlanner.finish(arms, p, Feedback.EASY, monday).progress
        assertEquals(2f, p.level)
        p = PackPlanner.finish(arms, p, Feedback.HARD, monday.plusDays(2)).progress
        assertEquals(1.5f, p.level)
        // nunca abaixo do 1
        p = PackPlanner.finish(arms, p.copy(level = 1f), Feedback.HARD, monday.plusDays(4)).progress
        assertEquals(1f, p.level)
    }

    @Test
    fun quemJaTreinaComecaMaisAlto() {
        assertEquals(1f, PackPlanner.startLevel(arms, ExerciseFrequency.NEVER))
        assertEquals(7f, PackPlanner.startLevel(arms, ExerciseFrequency.DAILY))
        assertEquals(1f, PackPlanner.startLevel(PackCatalog.get(PackId.STRETCH_CHAIR), ExerciseFrequency.DAILY))
    }

    @Test
    fun agendaPadrao() {
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), PackPlanner.defaultDays(3))
        assertEquals(7, PackPlanner.defaultDays(7).size)
        val p = PackPlanner.start(arms, null, monday)
        assertTrue(p.scheduledOn(monday))
        assertFalse(p.scheduledOn(monday.plusDays(1)))
        assertEquals(listOf(PackId.ARMS), PackPlanner.dueToday(mapOf(PackId.ARMS to p), monday))
        val done = PackPlanner.finish(arms, p, Feedback.OK, monday).progress
        assertTrue(PackPlanner.dueToday(mapOf(PackId.ARMS to done), monday).isEmpty())
    }

    @Test
    fun bonusDoTreinoSoUmaVezPorDiaEDaSemanaAoCompletar() {
        var p = PackPlanner.start(arms, null, monday)
        val r1 = PackPlanner.finish(arms, p, Feedback.OK, monday)
        assertEquals(PackRules.WORKOUT_BONUS, r1.workoutBonus)
        assertEquals(0L, r1.weekBonus)
        val again = PackPlanner.finish(arms, r1.progress, Feedback.OK, monday)
        assertEquals(0L, again.workoutBonus)
        p = PackPlanner.finish(arms, r1.progress, Feedback.OK, monday.plusDays(2)).progress
        val r3 = PackPlanner.finish(arms, p, Feedback.OK, monday.plusDays(4))
        assertEquals(PackRules.WEEK_BONUS, r3.weekBonus)
        // a mesma semana não paga duas vezes
        assertEquals(0L, PackPlanner.finish(arms, r3.progress, Feedback.OK, monday.plusDays(5)).weekBonus)
        assertEquals(2, r3.progress.week(monday.plusDays(7)))
    }

    @Test
    fun testeACada2SemanasEEvolucao() {
        var p = PackPlanner.start(arms, null, monday)
        assertTrue(p.testDue(monday))
        p = PackPlanner.recordTest(arms, p, 10, monday) // 10 na parede = 4
        assertFalse(p.testDue(monday.plusDays(13)))
        assertTrue(p.testDue(monday.plusDays(14)))
        assertNull(p.evolutionPercent())
        p = PackPlanner.recordTest(arms, p.copy(level = 9f), 8, monday.plusDays(14)) // 8 no joelho = 6
        assertEquals(50, p.evolutionPercent())
    }

    @Test
    fun guardaELeDeVolta() {
        var p = PackPlanner.start(arms, ExerciseFrequency.DAILY, monday)
        p = PackPlanner.finish(arms, p, Feedback.EASY, monday).progress
        p = PackPlanner.recordTest(arms, p, 12, monday)
        assertEquals(p, PackCodec.decode(PackCodec.encode(p)))
        assertEquals(PackProgress(), PackCodec.decode(""))
        assertEquals(PackProgress(), PackCodec.decode("lixo|x=1|lv=abc"))
    }
}
