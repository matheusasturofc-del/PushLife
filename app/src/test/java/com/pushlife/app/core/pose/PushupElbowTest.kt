package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// flexão pelo cotovelo, sem classificador (ângulos que o Google não pega)
class PushupElbowTest {
    // de lado: mãos no chão (y = 400), corpo desce junto com os ombros
    private fun pushup(elbowAngle: Float): PoseFrame {
        val arm = 100f
        val d = sqrt(2 * arm * arm * (1 - cos(Math.toRadians(elbowAngle.toDouble())))).toFloat()
        val wrist = Point(200f, 400f)
        val shoulder = Point(200f, 400f - d)
        val h = sqrt((arm * arm - (d / 2) * (d / 2)).coerceAtLeast(0f))
        val elbow = Point(200f + h, 400f - d / 2)
        val y = shoulder.y
        return PoseFrame(
            mapOf(
                Joint.NOSE to Point(150f, y - 20f),
                Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to elbow, Joint.LEFT_WRIST to wrist,
                Joint.LEFT_HIP to Point(400f, y + 5f), Joint.LEFT_KNEE to Point(500f, y + 10f), Joint.LEFT_ANKLE to Point(600f, y + 15f),
            ),
        )
    }

    // rosca em pé: punho abaixo do ombro, mas tronco reto
    private fun standingCurl(elbowAngle: Float): PoseFrame {
        val r = Math.toRadians((180f - elbowAngle).toDouble())
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to Point(300f, 200f), Joint.LEFT_ELBOW to Point(300f, 300f),
                Joint.LEFT_WRIST to Point(300f + (100 * sin(r)).toFloat(), 300f + (100 * cos(r)).toFloat()),
                Joint.LEFT_HIP to Point(300f, 450f), Joint.LEFT_KNEE to Point(300f, 550f), Joint.LEFT_ANKLE to Point(300f, 650f),
            ),
        )
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    @Test
    fun cotoveloA110GrausJaConta() {
        val tr = PushupTracker()
        tr.feed(List(3) { List(4) { pushup(170f) } + List(4) { pushup(110f) } }.flatten() + List(4) { pushup(170f) })
        assertEquals(3, tr.count)
    }

    @Test
    fun dobrarPoucoNaoContaEPedeParaDescer() {
        val tr = PushupTracker()
        val ups = tr.feed(List(4) { pushup(170f) } + List(4) { pushup(135f) } + List(4) { pushup(170f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun emPeDobrandoOBracoNaoConta() {
        val tr = PushupTracker()
        tr.feed(List(4) { List(4) { standingCurl(170f) } + List(4) { standingCurl(60f) } }.flatten())
        assertEquals(0, tr.count)
    }

    @Test
    fun mexerSoACabecaComBracosEsticadosNaoConta() {
        val tr = PushupTracker()
        val frames = List(40) { i ->
            val base = pushup(170f)
            val nose = base[Joint.NOSE]!!
            PoseFrame(Joint.entries.mapNotNull { j -> base[j]?.let { j to it } }.toMap() + (Joint.NOSE to Point(nose.x, nose.y + if ((i / 5) % 2 == 0) 0f else 80f)))
        }
        tr.feed(frames)
        assertEquals(0, tr.count)
    }
}
