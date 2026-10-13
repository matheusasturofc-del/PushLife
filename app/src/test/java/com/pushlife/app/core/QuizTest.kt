package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTest {
    @Test
    fun indiceSomaHorasAppsEMotivos() {
        assertEquals(25, Quiz.dependenceScore(5f, apps = 0, reasons = 0)) // 5/12 × 60
        assertEquals(65, Quiz.dependenceScore(5f, apps = 3, reasons = 3))
        assertEquals(100, Quiz.dependenceScore(20f, apps = 9, reasons = 9)) // nunca passa de 100
        assertEquals(0, Quiz.dependenceScore(0f, 0, 0))
    }

    @Test
    fun metaFicaAbaixoDoResultado() {
        assertEquals(15, Quiz.goalScore(3f))
        assertTrue(Quiz.goalScore(3f) < Quiz.dependenceScore(5f, 1, 1))
    }

    @Test
    fun anosDeVidaNoCelular() {
        assertEquals(15, Quiz.lifeYears(5f)) // 5 ÷ 24 × 70 = 14,6
        assertEquals(6, Quiz.yearsRecovered(5f, 3f)) // 2 ÷ 24 × 70 = 5,8
        assertEquals(0, Quiz.yearsRecovered(3f, 5f))
    }
}
