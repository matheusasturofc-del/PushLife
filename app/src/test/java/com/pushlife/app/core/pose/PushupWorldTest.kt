package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Flexão com a câmera de frente (celular no chão, na frente da cabeça): na imagem plana o braço parece
 * sempre esticado, porque o cotovelo dobra "para dentro" da tela. Com os pontos 3D tem que contar.
 */
class PushupWorldTest {
    private fun p(x: Float, y: Float, w: Vec3) = Point(x, y, world = w)

    /** Ombro sobre a mão; o cotovelo dobra para trás (z), o ombro desce (y). Metros, y para baixo. */
    private fun frontPushup(elbowAngle: Float): PoseFrame {
        val arm = 0.3f
        val d = sqrt(2 * arm * arm * (1 - cos(Math.toRadians(elbowAngle.toDouble())))).toFloat()
        val h = sqrt((arm * arm - (d / 2) * (d / 2)).coerceAtLeast(0f))
        val wrist = Vec3(0.2f, 0.5f, 0f)
        val shoulder = Vec3(0.2f, 0.5f - d, 0f)
        val elbow = Vec3(0.2f, 0.5f - d / 2, h)
        val hip = Vec3(0.2f, shoulder.y + 0.05f, 0.5f) // tronco deitado, indo para longe da câmera
        // Na imagem: tudo numa reta vertical, o braço "esticado" o tempo todo.
        val sy = 300f - d * 100f
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to p(200f, sy, shoulder), Joint.LEFT_ELBOW to p(200f, sy + 40f, elbow),
                Joint.LEFT_WRIST to p(200f, sy + 80f, wrist), Joint.LEFT_HIP to p(200f, sy - 10f, hip),
                Joint.LEFT_KNEE to p(200f, sy - 15f, Vec3(0.2f, hip.y + 0.05f, 0.9f)),
                Joint.LEFT_ANKLE to p(200f, sy - 18f, Vec3(0.2f, hip.y + 0.1f, 1.3f)),
            ),
        )
    }

    /** Em pé de frente para a câmera dobrando o braço (rosca), em 3D. */
    private fun standingCurl(elbowAngle: Float): PoseFrame {
        val r = Math.toRadians((180f - elbowAngle).toDouble())
        val shoulder = Vec3(0f, -0.4f, 0f)
        val elbow = Vec3(0f, -0.1f, 0f)
        val wrist = Vec3(0f, -0.1f + (0.3 * cos(r)).toFloat(), -(0.3 * sin(r)).toFloat())
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to p(300f, 200f, shoulder), Joint.LEFT_ELBOW to p(300f, 300f, elbow),
                Joint.LEFT_WRIST to p(300f, 320f, wrist), Joint.LEFT_HIP to p(300f, 450f, Vec3(0f, 0.1f, 0f)),
                Joint.LEFT_KNEE to p(300f, 550f, Vec3(0f, 0.5f, 0f)), Joint.LEFT_ANKLE to p(300f, 650f, Vec3(0f, 0.9f, 0f)),
            ),
        )
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    @Test
    fun deFrenteContaPeloCotoveloEm3d() {
        val tr = PushupTracker()
        tr.feed(List(4) { List(4) { frontPushup(170f) } + List(4) { frontPushup(105f) } }.flatten() + List(4) { frontPushup(170f) })
        assertEquals(4, tr.count)
    }

    @Test
    fun deFrenteDescerPoucoNaoConta() {
        val tr = PushupTracker()
        val ups = tr.feed(List(4) { frontPushup(170f) } + List(4) { frontPushup(138f) } + List(4) { frontPushup(170f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.GO_LOWER, ups.last().cue)
    }

    @Test
    fun emPeDeFrenteDobrandoOBracoNaoConta() {
        val tr = PushupTracker()
        tr.feed(List(4) { List(4) { standingCurl(170f) } + List(4) { standingCurl(50f) } }.flatten())
        assertEquals(0, tr.count)
    }

    @Test
    fun angulo3dIgnoraAImagemPlana() {
        val f = frontPushup(90f)
        val a = Geometry.angle(f[Joint.LEFT_SHOULDER]!!, f[Joint.LEFT_ELBOW]!!, f[Joint.LEFT_WRIST]!!)
        assertEquals(90f, a, 1f)
    }
}
