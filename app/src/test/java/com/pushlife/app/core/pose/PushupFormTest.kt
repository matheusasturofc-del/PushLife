package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

import kotlin.math.sqrt

/** Flexão de joelhos (não vale) e de frente com as mãos fora da imagem (vale). */
class PushupFormTest {
    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    /** De perfil, como no PushupElbowTest, mas com o joelho no chão: perna dobrada a ~90°. */
    private fun kneePushup(elbowAngle: Float): PoseFrame {
        val arm = 100f
        val d = sqrt(2 * arm * arm * (1 - cos(Math.toRadians(elbowAngle.toDouble())))).toFloat()
        val shoulder = Point(200f, 400f - d)
        val h = sqrt((arm * arm - (d / 2) * (d / 2)).coerceAtLeast(0f))
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to Point(200f + h, 400f - d / 2), Joint.LEFT_WRIST to Point(200f, 400f),
                Joint.LEFT_HIP to Point(380f, shoulder.y + 40f), Joint.LEFT_KNEE to Point(500f, 400f), Joint.LEFT_ANKLE to Point(500f, 300f),
            ),
        )
    }

    @Test
    fun deJoelhosNaoContaEAvisa() {
        val tr = PushupTracker { null }
        val ups = tr.feed(List(4) { List(4) { kneePushup(170f) } + List(4) { kneePushup(90f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.KNEES_UP, ups.last().cue)
    }

    /**
     * De frente, celular no chão perto das mãos: as mãos ficam fora da imagem (sem punho). Os ombros
     * (100 px de largura) descem até [drop] px.
     */
    private fun frontNoHands(drop: Float): PoseFrame {
        val y = 200f + drop
        return PoseFrame(
            mapOf(
                Joint.NOSE to Point(300f, y - 30f),
                Joint.LEFT_SHOULDER to Point(250f, y), Joint.RIGHT_SHOULDER to Point(350f, y),
                Joint.LEFT_ELBOW to Point(235f, y + 70f), Joint.RIGHT_ELBOW to Point(365f, y + 70f),
                Joint.LEFT_WRIST to Point(230f, y + 140f, visibility = 0.1f), Joint.RIGHT_WRIST to Point(370f, y + 140f, visibility = 0.1f),
            ),
        )
    }

    @Test
    fun deFrenteSemAsMaosContaPelaDescidaDosOmbros() {
        val tr = PushupTracker { null }
        tr.feed(List(4) { List(4) { frontNoHands(0f) } + List(4) { frontNoHands(45f) } }.flatten() + List(4) { frontNoHands(0f) })
        assertEquals(4, tr.count)
    }

    @Test
    fun deFrenteDescerPoucoOsOmbrosNaoConta() {
        val tr = PushupTracker { null }
        tr.feed(List(4) { List(4) { frontNoHands(0f) } + List(4) { frontNoHands(18f) } }.flatten())
        assertEquals(0, tr.count)
    }

    @Test
    fun deFrenteCabecaMexendoNaoConta() {
        val tr = PushupTracker { null }
        tr.feed(List(40) { i ->
            val f = frontNoHands(0f)
            PoseFrame(Joint.entries.mapNotNull { j -> f[j]?.let { j to it } }.toMap() + (Joint.NOSE to Point(300f, 170f + if ((i / 4) % 2 == 0) 0f else 60f)))
        })
        assertEquals(0, tr.count)
    }

    @Test
    fun ficarParadoEmbaixoDeFrenteNaoContaSozinho() {
        val tr = PushupTracker { null }
        tr.feed(List(4) { frontNoHands(0f) } + List(40) { frontNoHands(45f) })
        assertEquals(0, tr.count)
    }

}
