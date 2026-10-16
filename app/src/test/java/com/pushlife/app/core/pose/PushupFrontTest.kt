package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

/**
 * Celular no chão, na frente da cabeça (só imagem plana, como o ML Kit entrega): o cotovelo dobra
 * "para dentro" da tela, então na imagem o braço parece sempre reto. O que muda é o ombro descendo
 * até a mão.
 */
class PushupFrontTest {
    /** Braço de 100 px por segmento; o ombro fica a 2·100·sen(ângulo/2) da mão, bem em cima dela. */
    private fun front(elbowAngle: Float, jitter: Float = 0f): PoseFrame {
        val reach = (200 * sin(Math.toRadians(elbowAngle / 2.0))).toFloat() + jitter
        val wristY = 450f
        val shoulderY = wristY - reach
        fun side(x: Float) = listOf(
            Point(x, shoulderY), Point(x, shoulderY + reach / 2), Point(x, wristY),
            Point(x - 5f, shoulderY + 30f), Point(x - 8f, shoulderY + 45f), Point(x - 10f, shoulderY + 55f),
        )
        val l = side(250f)
        val r = side(350f)
        return PoseFrame(
            mapOf(
                Joint.NOSE to Point(300f, shoulderY - 40f),
                Joint.LEFT_SHOULDER to l[0], Joint.LEFT_ELBOW to l[1], Joint.LEFT_WRIST to l[2],
                Joint.LEFT_HIP to l[3], Joint.LEFT_KNEE to l[4], Joint.LEFT_ANKLE to l[5],
                Joint.RIGHT_SHOULDER to r[0], Joint.RIGHT_ELBOW to r[1], Joint.RIGHT_WRIST to r[2],
                Joint.RIGHT_HIP to r[3], Joint.RIGHT_KNEE to r[4], Joint.RIGHT_ANKLE to r[5],
            ),
        )
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    @Test
    fun deFrenteContaQuandoOOmbroDesceAteAMao() {
        val tr = PushupTracker { null }
        tr.feed(List(5) { List(4) { front(172f) } + List(4) { front(110f) } }.flatten() + List(4) { front(172f) })
        assertEquals(5, tr.count)
    }

    @Test
    fun deFrenteDescerPoucoNaoContaEPedeParaDescer() {
        val tr = PushupTracker { null }
        val ups = tr.feed(List(4) { front(172f) } + List(4) { front(130f) } + List(4) { front(172f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun tremidaDaCameraNaoConta() {
        val tr = PushupTracker { null }
        tr.feed(List(60) { i -> front(172f, jitter = if (i % 2 == 0) 6f else -6f) })
        assertEquals(0, tr.count)
    }

    @Test
    fun ficarEmbaixoUnsSegundosNaoContaSozinho() {
        val tr = PushupTracker { null }
        // Desce e fica 6 s embaixo: a referência esquece devagar, mas não a ponto de parecer que subiu.
        tr.feed(List(4) { front(172f) } + List(40) { front(100f) })
        assertEquals(0, tr.count)
    }

    @Test
    fun celularLentoComDoisQuadrosPorSegundoAindaConta() {
        val tr = PushupTracker { null }
        // Um quadro em cima e um embaixo por flexão: antes a suavização "comia" a descida.
        tr.feed(List(5) { listOf(front(172f), front(105f)) }.flatten() + front(172f), stepMs = 500)
        assertEquals(5, tr.count)
    }

    @Test
    fun mostraOQueEstaVendo() {
        val tr = PushupTracker { null }
        val u = tr.feed(List(3) { front(172f) } + front(110f)).last()
        assert(u.debug!!.contains("ombro")) { u.debug!! }
    }
}
