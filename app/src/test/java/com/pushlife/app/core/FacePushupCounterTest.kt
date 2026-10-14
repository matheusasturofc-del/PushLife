package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacePushupCounterTest {

    /** Simula o rosto indo de [from] até [to] em [steps] quadros (~30 fps). */
    private fun FacePushupCounter.sweep(from: Float, to: Float, start: Long, steps: Int = 10): Long {
        var t = start
        for (i in 0..steps) {
            update(from + (to - from) * i / steps, t)
            t += 33
        }
        return t
    }

    @Test
    fun contaUmaFlexaoPorCicloCompleto() {
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        repeat(3) {
            t = c.sweep(0.30f, 0.60f, t) // desce: rosto dobra de tamanho
            t = c.sweep(0.60f, 0.30f, t) // sobe
        }
        assertEquals(3, c.count)
    }

    @Test
    fun naoContaMeiaFlexaoEAvisa() {
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        t = c.sweep(0.30f, 0.37f, t) // desceu pouco (1,23×)
        c.sweep(0.37f, 0.30f, t)
        assertEquals(0, c.count)
        assertTrue(c.lastPartialMs > 0)
    }

    @Test
    fun rostoQueSomeDePertoContaComoDescida() {
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        t = c.sweep(0.30f, 0.42f, t) // chegando perto...
        repeat(5) { c.update(null, t); t += 33 } // ...e o rosto saiu da imagem
        assertEquals(FacePushupCounter.Phase.DOWN, c.phase)
        c.sweep(0.50f, 0.30f, t)
        assertEquals(1, c.count)
    }

    @Test
    fun rostoQueSomeLongeNaoContaNada() {
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        repeat(5) { c.update(null, t); t += 33 } // saiu de perto do celular
        c.sweep(0.30f, 0.30f, t)
        assertEquals(0, c.count)
        assertEquals(FacePushupCounter.Phase.UP, c.phase)
    }

    @Test
    fun aprendeOTopoSeComecarEmbaixo() {
        val c = FacePushupCounter()
        var t = c.sweep(0.60f, 0.60f, 0, steps = 5) // começou já embaixo
        t = c.sweep(0.60f, 0.30f, t) // sobe: não é repetição, só aprende o topo
        assertEquals(0, c.count)
        assertTrue("subir do fundo não é meia flexão", c.lastPartialMs < 0)
        t = c.sweep(0.30f, 0.60f, t)
        c.sweep(0.60f, 0.30f, t)
        assertEquals(1, c.count)
    }

    @Test
    fun ignoraTremidaRapidaDemais() {
        val c = FacePushupCounter(smoothing = 1f, minRepMs = 400)
        c.update(0.30f, 0)
        c.update(0.60f, 50); c.update(0.30f, 100) // conta
        c.update(0.60f, 150); c.update(0.30f, 200) // rápido demais
        c.update(0.60f, 700); c.update(0.30f, 800) // conta
        assertEquals(2, c.count)
    }

    @Test
    fun profundidadeVaiDeZeroAUm() {
        val c = FacePushupCounter(smoothing = 1f)
        c.update(0.30f, 0)
        assertEquals(0f, c.depth, 0.001f)
        c.update(0.30f * 1.175f, 10) // metade do caminho até 1,35×
        assertTrue(c.depth in 0.45f..0.55f)
        c.update(0.60f, 20)
        assertEquals(1f, c.depth, 0.001f)
    }

    @Test
    fun aprendeAAmplitudeDeQuemDesceMenos() {
        // Celular mais longe: o rosto só chega a 1,3× do tamanho de cima (abaixo do limite padrão de 1,35×).
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        repeat(5) {
            t = c.sweep(0.30f, 0.39f, t)
            t = c.sweep(0.39f, 0.30f, t)
        }
        assertTrue("as primeiras viram calibração; as seguintes contam", c.count >= 3)
    }

    @Test
    fun calibracaoNaoContaTremidaPequena() {
        val c = FacePushupCounter()
        var t = c.sweep(0.30f, 0.30f, 0, steps = 5)
        repeat(6) {
            t = c.sweep(0.30f, 0.33f, t) // 1,1×: balanço, não flexão
            t = c.sweep(0.33f, 0.30f, t)
        }
        assertEquals(0, c.count)
    }
}
