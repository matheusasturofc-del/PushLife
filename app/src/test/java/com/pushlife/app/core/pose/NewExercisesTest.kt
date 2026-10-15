package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import com.pushlife.app.core.Recommendations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

class NewExercisesTest {
    private fun toward(from: Point, deg: Float, len: Float = 100f): Point {
        val r = Math.toRadians(deg.toDouble())
        return Point(from.x + (len * sin(r)).toFloat(), from.y - (len * cos(r)).toFloat())
    }

    /** Pessoa de perfil com o joelho em [kneeAngle] e o tronco inclinado [lean] graus. */
    private fun legs(kneeAngle: Float, lean: Float = 5f, otherKnee: Float? = null): PoseFrame {
        val ankle = Point(300f, 600f)
        val knee = Point(300f, 500f)
        val hip = toward(knee, -(180f - kneeAngle))
        val shoulder = toward(hip, lean, 150f)
        val points = mutableMapOf(
            Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to toward(shoulder, 180f, 60f), Joint.LEFT_WRIST to toward(shoulder, 180f, 120f),
            Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle,
        )
        if (otherKnee != null) {
            val k2 = Point(360f, 520f)
            points[Joint.RIGHT_HIP] = hip
            points[Joint.RIGHT_KNEE] = k2
            points[Joint.RIGHT_ANKLE] = toward(k2, 180f - (180f - otherKnee) + 0f).let { Point(it.x, it.y) }
        }
        return PoseFrame(points)
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    @Test
    fun afundoContaDescendoESubindo() {
        val tr = LungeTracker()
        val frames = List(3) { List(4) { legs(175f) } + List(4) { legs(90f) } }.flatten() + List(4) { legs(175f) }
        tr.feed(frames)
        assertEquals(3, tr.count)
    }

    @Test
    fun afundoRasoNaoConta() {
        val tr = LungeTracker()
        val ups = tr.feed(List(4) { legs(175f) } + List(4) { legs(118f) } + List(4) { legs(175f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun isometricoContaSegundosParadoNaPosicao() {
        val tr = WallSitTracker()
        val ups = tr.feed(List(51) { legs(95f) }, stepMs = 100) // 5 s
        assertEquals(5, tr.count)
        assertEquals(Cue.HOLD, ups.last().cue)
    }

    @Test
    fun isometricoEmPeNaoContaEPedeParaDescer() {
        val tr = WallSitTracker()
        val ups = tr.feed(List(30) { legs(170f) }, stepMs = 100)
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun semHistoricoRecomendaFlexaoEAbdominal() {
        assertEquals(listOf(Exercise.PUSHUP, Exercise.SITUP), Recommendations.top(emptyMap()))
    }

    @Test
    fun recomendaOsMaisPraticados() {
        val d = LocalDate.of(2026, 10, 1)
        val history = mapOf(
            d to mapOf(Exercise.SQUAT to 20, Exercise.PLANK to 60),
            d.plusDays(1) to mapOf(Exercise.SQUAT to 15, Exercise.LUNGE to 10),
            d.plusDays(2) to mapOf(Exercise.LUNGE to 12, Exercise.SQUAT to 10),
        )
        assertEquals(listOf(Exercise.SQUAT, Exercise.LUNGE), Recommendations.top(history))
    }

    @Test
    fun soUmPraticadoCompletaComFlexao() {
        val history = mapOf(LocalDate.of(2026, 10, 1) to mapOf(Exercise.SITUP to 30))
        assertEquals(listOf(Exercise.SITUP, Exercise.PUSHUP), Recommendations.top(history))
    }
}
