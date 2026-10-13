package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeBankTest {
    private val day = LocalDate.of(2026, 10, 13)

    @Test
    fun cadaFlexaoValeUmMinuto() {
        val r = TimeBank.addReps(BankState(), 25, day)
        assertEquals(25 * 60L, r.creditedSeconds)
        assertEquals(25, r.state.repsOn(day))
        assertEquals(25, r.state.totalReps)
        assertFalse(r.hitCap)
    }

    @Test
    fun saldoGanhoParaEmDuasHoras() {
        val r = TimeBank.addReps(BankState(earnedSeconds = 110 * 60L), 20, day)
        assertEquals(Rules.MAX_EARNED_SECONDS, r.state.earnedSeconds)
        assertEquals(10 * 60L, r.creditedSeconds)
        assertTrue(r.hitCap)
        assertEquals(20, r.state.repsOn(day), "as repetições contam mesmo acima do limite")
    }

    @Test
    fun umaHoraGratisPorDiaQueNaoAcumula() {
        val s = BankState()
        assertEquals(3600L, s.freeRemaining(day))
        val usado = TimeBank.spend(s, 40 * 60L, day)
        assertEquals(20 * 60L, usado.freeRemaining(day))
        assertEquals(3600L, usado.freeRemaining(day.plusDays(1)), "no dia seguinte volta a 1 h, sem somar")
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

    private fun assertEquals(expected: Int, actual: Int, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
    private fun assertEquals(expected: Long, actual: Long, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
}
