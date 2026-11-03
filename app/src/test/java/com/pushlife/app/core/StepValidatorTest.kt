package com.pushlife.app.core

import com.pushlife.app.core.steps.StepReject
import com.pushlife.app.core.steps.StepValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepValidatorTest {
    // anda por [seconds] s com [perMin] passos/min, giro [rotation] e (se houver) GPS [speed]
    private fun StepValidator.walk(
        from: Long,
        seconds: Int,
        perMin: Int,
        startTotal: Int,
        rotation: Float = 1f,
        speed: Float? = null,
        accuracy: Float = 8f,
    ): Pair<Long, Int> {
        var total = startTotal
        var t = from
        repeat(seconds) { s ->
            val stepsThisSecond = ((s + 1) * perMin / 60) - (s * perMin / 60)
            total += stepsThisSecond
            repeat(10) { k -> onRotation(rotation, t + k * 100L) }
            speed?.let { onLocation(it, accuracy, t) }
            onSteps(total, t + 999)
            t += 1000
        }
        return t to total
    }

    @Test
    fun caminhadaNormalConta() {
        val v = StepValidator()
        val (t, total) = v.walk(0, 60, 110, 0, speed = 1.3f)
        v.finish(t)
        assertEquals(total, v.validSteps)
        assertEquals(0, v.totalRejected)
    }

    @Test
    fun balancoDeBracoNaoConta() {
        val v = StepValidator()
        val (t, _) = v.walk(0, 60, 110, 0, rotation = 5f)
        v.finish(t)
        assertEquals(0, v.validSteps)
        assertEquals(StepReject.ARM_SWING, v.rejectedSteps.keys.single())
    }

    @Test
    fun deCarroNaoConta() {
        val v = StepValidator()
        val (t, _) = v.walk(0, 60, 60, 0, speed = 12f)
        v.finish(t)
        assertEquals(0, v.validSteps)
        assertEquals(StepReject.VEHICLE, v.lastReject)
    }

    @Test
    fun paradoComGpsBomNaoConta() {
        val v = StepValidator()
        val (t, _) = v.walk(0, 40, 100, 0, speed = 0.05f, accuracy = 5f)
        v.finish(t)
        assertEquals(0, v.validSteps)
        assertEquals(StepReject.NOT_MOVING, v.lastReject)
    }

    @Test
    fun gpsRuimDentroDeCasaNaoAtrapalha() {
        val v = StepValidator()
        val (t, total) = v.walk(0, 40, 100, 0, speed = 0f, accuracy = 60f)
        v.finish(t)
        assertEquals(total, v.validSteps)
    }

    @Test
    fun sacudirRapidoNaoConta() {
        val v = StepValidator()
        val (t, _) = v.walk(0, 30, 300, 0)
        v.finish(t)
        assertEquals(0, v.validSteps)
        assertEquals(StepReject.TOO_FAST, v.lastReject)
    }

    @Test
    fun trechoCurtoNaoConta() {
        val v = StepValidator()
        // 8 s andando e para
        var (t, total) = v.walk(0, 8, 110, 0)
        t += 15_000
        v.tick(t)
        v.finish(t)
        assertEquals(0, v.validSteps)
        assertEquals(total, v.rejectedSteps[StepReject.TOO_SHORT])
    }

    @Test
    fun voltaAContarDepoisDeUmTrechoRuim() {
        val v = StepValidator()
        var (t, total) = v.walk(0, 30, 110, 0, rotation = 5f) // balançando
        val before = total
        val r = v.walk(t, 40, 110, total) // andando de verdade
        t = r.first
        total = r.second
        v.finish(t)
        assertEquals(total - before, v.validSteps)
        assertNull(v.lastReject)
    }
}
