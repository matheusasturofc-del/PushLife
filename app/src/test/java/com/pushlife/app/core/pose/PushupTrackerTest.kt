package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

class PushupTrackerTest {
    /**
     * Flexão de perfil: cabeça à esquerda, pés à direita, mãos no chão (y = 400) embaixo dos ombros.
     * [elbowAngle] é o ângulo ombro-cotovelo-punho; o corpo todo desce junto com os ombros.
     */
    private fun pushup(elbowAngle: Float): PoseFrame {
        val arm = 100f
        val d = sqrt(2 * arm * arm * (1 - cos(Math.toRadians(elbowAngle.toDouble())))).toFloat()
        val wrist = Point(200f, 400f)
        val shoulder = Point(200f, 400f - d)
        val h = sqrt((arm * arm - (d / 2) * (d / 2)).coerceAtLeast(0f))
        val elbow = Point(200f + h, 400f - d / 2) // cotovelo vai para trás, rente ao corpo
        val y = shoulder.y
        return PoseFrame(
            mapOf(
                Joint.NOSE to Point(150f, y - 20f),
                Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to elbow, Joint.LEFT_WRIST to wrist,
                Joint.LEFT_HIP to Point(400f, y + 5f), Joint.LEFT_KNEE to Point(500f, y + 10f), Joint.LEFT_ANKLE to Point(600f, y + 15f),
            ),
        )
    }

    /** Em pé, dobrando o braço (rosca): tronco na vertical, punho abaixo do ombro. */
    private fun standingCurl(elbowAngle: Float): PoseFrame {
        val shoulder = Point(300f, 200f)
        val elbow = Point(300f, 300f)
        val r = Math.toRadians((180f - elbowAngle).toDouble())
        val wrist = Point(300f + (100 * kotlin.math.sin(r)).toFloat(), 300f + (100 * cos(r)).toFloat())
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to elbow, Joint.LEFT_WRIST to wrist,
                Joint.LEFT_HIP to Point(300f, 400f), Joint.LEFT_KNEE to Point(300f, 500f), Joint.LEFT_ANKLE to Point(300f, 600f),
            ),
        )
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    private fun reps(n: Int, down: Float = 80f): List<PoseFrame> =
        List(n) { List(3) { pushup(170f) } + List(3) { pushup(down) } }.flatten() + List(3) { pushup(170f) }

    @Test
    fun contaFlexoesDeVerdade() {
        val tr = PushupTracker()
        tr.feed(reps(3))
        assertEquals(3, tr.count)
    }

    @Test
    fun mexerSoACabecaNaoConta() {
        val tr = PushupTracker()
        // Braços sempre esticados; só o nariz sobe e desce.
        val frames = List(30) { i ->
            val base = pushup(170f)
            val nose = base[Joint.NOSE]!!
            PoseFrame(
                Joint.entries.mapNotNull { j -> base[j]?.let { j to it } }.toMap() +
                    (Joint.NOSE to Point(nose.x, nose.y + if (i % 6 < 3) 0f else 60f)),
            )
        }
        tr.feed(frames)
        assertEquals(0, tr.count)
    }

    @Test
    fun dobrarOBracoEmPeNaoConta() {
        val tr = PushupTracker()
        val ups = tr.feed(List(4) { List(3) { standingCurl(170f) } + List(3) { standingCurl(60f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.GET_READY, ups.last().cue)
    }

    @Test
    fun descerPoucoNaoContaEPedeParaDescerMais() {
        val tr = PushupTracker()
        val ups = tr.feed(List(3) { pushup(170f) } + List(3) { pushup(115f) } + List(3) { pushup(170f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun sairDaPosicaoNoMeioNaoConta() {
        val tr = PushupTracker()
        tr.feed(List(3) { pushup(170f) } + List(3) { pushup(80f) } + List(3) { standingCurl(170f) } + List(3) { pushup(170f) })
        assertEquals(0, tr.count)
    }
}
