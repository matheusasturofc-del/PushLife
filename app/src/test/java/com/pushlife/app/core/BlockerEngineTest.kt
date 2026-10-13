package com.pushlife.app.core

import com.pushlife.app.core.BlockerEngine.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerEngineTest {
    private val blocked = setOf("com.instagram.android", "com.zhiliaoapp.musically")
    private val insta = "com.instagram.android"
    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun appsLiberadosNaoGastamNada() {
        val e = BlockerEngine()
        repeat(10) { e.tick(launcher, blocked, it * 1000L, 600, 0) }
        assertEquals(0L, e.pendingSeconds)
    }

    @Test
    fun entrarComSaldoAvisaEDepoisGastaUmSegundoPorSegundo() {
        val e = BlockerEngine()
        val first = e.tick(insta, blocked, 0, 600, 0)
        assertEquals(listOf(Event.Entered(insta, 600)), first)
        for (t in 1..30) e.tick(insta, blocked, t * 1000L, 600, 0)
        assertEquals(30L, e.pendingSeconds)
    }

    @Test
    fun entrarSemSaldoBloqueiaNaHora() {
        val e = BlockerEngine()
        assertEquals(listOf(Event.Block(insta)), e.tick(insta, blocked, 0, 0, 0))
    }

    @Test
    fun saldoAcabandoNoMeioDoUsoBloqueia() {
        val e = BlockerEngine()
        e.tick(insta, blocked, 0, 5, 0)
        val events = (1..6).flatMap { e.tick(insta, blocked, it * 1000L, 5, 0) }
        assertTrue(events.contains(Event.Block(insta)))
        assertEquals(5L, e.pendingSeconds) // nunca gasta mais do que tinha
    }

    @Test
    fun naoReabreABloqueioSemParar() {
        val e = BlockerEngine(reblockAfterMs = 3_000)
        val events = (0..10).flatMap { e.tick(insta, blocked, it * 500L, 0, 0) }
        assertEquals(2, events.count { it is Event.Block }) // em 0 s e em 3 s
    }

    @Test
    fun emergenciaLiberaSemGastar() {
        val e = BlockerEngine()
        val until = 60_000L
        val events = (0..30).flatMap { e.tick(insta, blocked, it * 1000L, 0, until) }
        assertTrue(events.none { it is Event.Block })
        assertEquals(0L, e.pendingSeconds)
    }

    @Test
    fun celularDormindoNaoCobraOBuraco() {
        val e = BlockerEngine(maxGapMs = 5_000)
        e.tick(insta, blocked, 0, 3600, 0)
        e.tick(insta, blocked, 10 * 60_000L, 3600, 0) // 10 min sem tique
        assertEquals(5L, e.pendingSeconds)
    }

    @Test
    fun sairDoAppAvisaEEntregaOGasto() {
        val e = BlockerEngine()
        e.tick(insta, blocked, 0, 600, 0)
        e.tick(insta, blocked, 2000, 600, 0)
        val events = e.tick(launcher, blocked, 3000, 600, 0)
        assertEquals(listOf(Event.Left(insta)), events)
        assertEquals(2L, e.takePending())
        assertEquals(0L, e.pendingSeconds)
    }
}
