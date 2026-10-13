package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeBankTest {
    private val day = LocalDate.of(2026, 10, 13)

    @Test
    fun cadaFlexaoValeUmMinuto() {
        val r = TimeBank.addExercise(BankState(), Exercise.PUSHUP, 25, day)
        assertEquals(25 * 60L, r.creditedSeconds)
        assertEquals(25, r.state.countOn(day, Exercise.PUSHUP))
        assertEquals(25, r.state.effortMinutesOn(day))
        assertFalse(r.hitCap)
    }

    @Test
    fun cadaExercicioTemSeuValor() {
        assertEquals(30L, Exercise.SQUAT.valueOf(1))
        assertEquals(15L, Exercise.JUMPING_JACK.valueOf(1))
        assertEquals(3L * 45, Exercise.PLANK.valueOf(45)) // 45 s de prancha
        assertEquals(60L, Exercise.WALK.valueOf(100)) // 100 passos = 1 min
        assertEquals(120L, Exercise.PULLUP.valueOf(1))
    }

    @Test
    fun exerciciosDiferentesSomamNaMetaDoDia() {
        var s = BankState()
        s = TimeBank.addExercise(s, Exercise.PUSHUP, 10, day).state
        s = TimeBank.addExercise(s, Exercise.SQUAT, 10, day).state
        assertEquals(15, s.effortMinutesOn(day))
        assertEquals(10, s.countOn(day, Exercise.SQUAT))
    }

    @Test
    fun saldoGanhoParaEmDuasHoras() {
        val r = TimeBank.addExercise(BankState(earnedSeconds = 110 * 60L), Exercise.PUSHUP, 20, day)
        assertEquals(Rules.MAX_EARNED_SECONDS, r.state.earnedSeconds)
        assertEquals(10 * 60L, r.creditedSeconds)
        assertTrue(r.hitCap)
        assertEquals("as repetições contam mesmo acima do limite", 20, r.state.countOn(day, Exercise.PUSHUP))
    }

    @Test
    fun umaHoraGratisPorDiaQueNaoAcumula() {
        val s = BankState()
        assertEquals(3600L, s.freeRemaining(day))
        val usado = TimeBank.spend(s, 40 * 60L, day)
        assertEquals(20 * 60L, usado.freeRemaining(day))
        assertEquals("no dia seguinte volta a 1 h, sem somar", 3600L, usado.freeRemaining(day.plusDays(1)))
    }

    @Test
    fun gastaPrimeiroOGratisDepoisOGanho() {
        val s = BankState(earnedSeconds = 30 * 60L)
        val depois = TimeBank.spend(s, 80 * 60L, day)
        assertEquals(0L, depois.freeRemaining(day))
        assertEquals(10 * 60L, depois.earnedSeconds)
        assertEquals(10 * 60L, depois.available(day))
    }

    @Test
    fun naoFicaNegativo() {
        val depois = TimeBank.spend(BankState(earnedSeconds = 60), 5 * 3600L, day)
        assertEquals(0L, depois.available(day))
        assertEquals(0L, depois.earnedSeconds)
    }

    @Test
    fun gratisDeOntemNaoAtrapalhaHoje() {
        val ontem = TimeBank.spend(BankState(), 3600L, day.minusDays(1))
        val hoje = TimeBank.spend(ontem, 600L, day)
        assertEquals(50 * 60L, hoje.freeRemaining(day))
    }
}

class EmergencyTest {
    private val day = LocalDate.of(2026, 10, 13)

    @Test
    fun emergenciaDeixaDevendoDezFlexoesELiberaCincoMinutos() {
        val s = TimeBank.emergency(BankState(), day, nowMs = 1_000)!!
        assertEquals(10, s.debtPushups)
        assertEquals(1_000 + 5 * 60_000L, s.emergencyUntilMs)
        assertEquals(2, s.emergenciesLeft(day))
    }

    @Test
    fun soTresEmergenciasPorDia() {
        var s = BankState()
        repeat(3) { s = TimeBank.emergency(s, day, 0)!! }
        assertNull(TimeBank.emergency(s, day, 0))
        assertEquals(3, s.emergenciesLeft(day.plusDays(1)))
    }

    @Test
    fun exercicioPagaADividaAntesDeVirarSaldo() {
        val devendo = BankState(debtSeconds = 600) // 10 flexões
        val r = TimeBank.addExercise(devendo, Exercise.PUSHUP, 15, day)
        assertEquals(600L, r.debtPaidSeconds)
        assertEquals(0L, r.state.debtSeconds)
        assertEquals(5 * 60L, r.creditedSeconds)
        assertFalse(r.hitCap)
    }

    @Test
    fun qualquerExercicioPagaADivida() {
        val r = TimeBank.addExercise(BankState(debtSeconds = 600), Exercise.SQUAT, 4, day) // 4 × 30 s
        assertEquals(480L, r.state.debtSeconds)
        assertEquals(8, r.state.debtPushups)
        assertEquals(0L, r.creditedSeconds)
    }
}
