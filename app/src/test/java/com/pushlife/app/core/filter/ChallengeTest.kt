package com.pushlife.app.core.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {
    private val lines = listOf("Pornografia rouba meu tempo.", "Eu escolho a vida real.", "O filtro fica ligado.")

    @Test fun ignoresCaseAccentsAndPunctuation() {
        assertEquals("eu escolho a vida real", Challenge.normalize("  Eu ESCOLHO, a vída   real! "))
    }

    @Test fun fullTextIsDone() {
        val p = Challenge.progress("pornografia rouba meu tempo eu escolho a vida real o filtro fica ligado", lines)
        assertTrue(p.done)
        assertEquals(3, p.linesDone)
    }

    @Test fun countsFinishedLinesOnly() {
        val p = Challenge.progress("Pornografia rouba meu tempo. Eu esc", lines)
        assertTrue(p.onTrack)
        assertFalse(p.done)
        assertEquals(1, p.linesDone)
    }

    @Test fun halfWordDoesNotFinishLine() {
        assertEquals(0, Challenge.progress("Pornografia rouba meu tem", lines).linesDone)
    }

    @Test fun mistakeIsOffTrack() {
        val p = Challenge.progress("Pornografia rouba seu", lines)
        assertFalse(p.onTrack)
        assertEquals("pornografia rouba ".length, p.correct)
    }

    @Test fun detectsPaste() {
        assertTrue(Challenge.looksPasted("", "Pornografia rouba meu tempo e minha energia."))
        assertFalse(Challenge.looksPasted("Porno", "Pornografia "))
    }
}
