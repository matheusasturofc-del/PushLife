package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

import kotlin.math.sqrt

/** Flexão de joelhos não vale. */
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
        val tr = PushupTracker()
        val ups = tr.feed(List(4) { List(4) { kneePushup(170f) } + List(4) { kneePushup(90f) } }.flatten())
        assertEquals(0, tr.count)
        assertEquals(Cue.KNEES_UP, ups.last().cue)
    }
}
