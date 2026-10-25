package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Flexão: só vale de lado, deitado, com o corpo reto e as pernas esticadas. */
class PushupFrontTest {
    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    /** De perfil: mão no chão (y = 400) embaixo do ombro; braço de 100 px por segmento. */
    private fun side(elbowAngle: Float, hipDy: Float = 5f, kneeAt: Point? = null): PoseFrame {
        val arm = 100f
        val d = sqrt(2 * arm * arm * (1 - cos(Math.toRadians(elbowAngle.toDouble())))).toFloat()
        val h = sqrt((arm * arm - (d / 2) * (d / 2)).coerceAtLeast(0f))
        val y = 400f - d
        val hip = Point(400f, y + hipDy)
        val ankle = Point(600f, y + 15f)
        // Perna reta: o joelho fica no meio do caminho entre o quadril e o tornozelo.
        val knee = kneeAt ?: Point((hip.x + ankle.x) / 2, (hip.y + ankle.y) / 2)
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to Point(200f, y), Joint.LEFT_ELBOW to Point(200f + h, 400f - d / 2), Joint.LEFT_WRIST to Point(200f, 400f),
                Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle,
            ),
        )
    }

    /** De frente: os dois ombros bem separados (100 px), braços descendo retos na imagem. */
    private fun front(elbowAngle: Float): PoseFrame {
        val reach = (200 * sin(Math.toRadians(elbowAngle / 2.0))).toFloat()
        val sy = 450f - reach
        fun s(x: Float) = listOf(Point(x, sy), Point(x, sy + reach / 2), Point(x, 450f), Point(x - 5f, sy + 30f), Point(x - 8f, sy + 45f), Point(x - 10f, sy + 55f))
        val l = s(250f)
        val r = s(350f)
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to l[0], Joint.LEFT_ELBOW to l[1], Joint.LEFT_WRIST to l[2], Joint.LEFT_HIP to l[3], Joint.LEFT_KNEE to l[4], Joint.LEFT_ANKLE to l[5],
                Joint.RIGHT_SHOULDER to r[0], Joint.RIGHT_ELBOW to r[1], Joint.RIGHT_WRIST to r[2], Joint.RIGHT_HIP to r[3], Joint.RIGHT_KNEE to r[4], Joint.RIGHT_ANKLE to r[5],
            ),
        )
    }

    @Test
    fun deFrentePedeParaVirarDeLadoENaoConta() {
        val tr = PushupTracker()
        val ups = tr.feed(List(4) { List(4) { front(172f) } + List(4) { front(100f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.TURN_SIDEWAYS, ups.last().cue)
    }

    @Test
    fun deLadoContaPeloCotovelo() {
        val tr = PushupTracker()
        tr.feed(List(5) { List(4) { side(170f) } + List(4) { side(110f) } }.flatten() + List(4) { side(170f) })
        assertEquals(5, tr.count)
    }

    @Test
    fun celularLentoComDoisQuadrosPorSegundoAindaConta() {
        val tr = PushupTracker()
        tr.feed(List(5) { listOf(side(170f), side(105f)) }.flatten() + side(170f), stepMs = 500)
        assertEquals(5, tr.count)
    }

    @Test
    fun quadrilCaidoNaoContaEPedeParaSubirOQuadril() {
        val tr = PushupTracker()
        // Quadril 90 px abaixo da linha ombro-pés: corpo "quebrado".
        val ups = tr.feed(List(3) { List(4) { side(170f, hipDy = 90f) } + List(4) { side(100f, hipDy = 90f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.HIPS_UP, ups.last().cue)
    }

    @Test
    fun quadrilEmpinadoNaoConta() {
        val tr = PushupTracker()
        val ups = tr.feed(List(3) { List(4) { side(170f, hipDy = -90f) } + List(4) { side(100f, hipDy = -90f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.HIPS_DOWN, ups.last().cue)
    }

    @Test
    fun saiDaPosicaoNoMeioEsqueceADescida() {
        val tr = PushupTracker()
        // Desce, levanta o quadril (sai da posição) e volta em cima: não pode contar.
        tr.feed(List(4) { side(170f) } + List(4) { side(100f) } + List(4) { side(100f, hipDy = -90f) } + List(4) { side(170f) })
        assertEquals(0, tr.count)
    }

    @Test
    fun mostraOQueEstaVendo() {
        val u = PushupTracker().feed(List(3) { side(170f) } + side(110f)).last()
        assertTrue(u.debug!!, u.debug!!.contains("cotovelo"))
    }

    @Test
    fun umQuadroRuimNoMeioNaoApagaAFlexao() {
        val tr = PushupTracker()
        // Embaixo, a IA perde o joelho por um quadro (perna "dobrada"): a flexão continua valendo.
        val glitch = side(100f, kneeAt = Point(500f, 450f))
        tr.feed(List(4) { side(170f) } + List(3) { side(100f) } + glitch + List(4) { side(170f) })
        assertEquals(1, tr.count)
    }
}
