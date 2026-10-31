package com.pushlife.app.core.pose

import com.pushlife.app.core.packs.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PackTrackersTest {
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    // anda len px a partir do ponto; 0° = pra cima, 90° = pra direita
    private fun toward(from: Point, deg: Float, len: Float = 100f) =
        Point(from.x + (len * sin(rad(deg))).toFloat(), from.y - (len * cos(rad(deg))).toFloat())

    // o detector sempre entrega todos os pontos; braço solto ao lado quando o teste não liga pra ele
    private fun frame(vararg joints: Pair<Joint, Point>): PoseFrame {
        val map = joints.toMap().toMutableMap()
        val shoulder = map.getValue(Joint.LEFT_SHOULDER)
        map.putIfAbsent(Joint.LEFT_ELBOW, Point(shoulder.x, shoulder.y + 50f))
        map.putIfAbsent(Joint.LEFT_WRIST, Point(shoulder.x, shoulder.y + 100f))
        return PoseFrame(map)
    }

    // de pé, de perfil, empurrando a parede: braço na horizontal, cotovelo com o ângulo pedido
    private fun wallPush(elbow: Float): PoseFrame {
        val ankle = Point(300f, 600f)
        val knee = toward(ankle, -10f)
        val hip = toward(knee, -10f)
        val shoulder = toward(hip, -10f)
        val elbowP = toward(shoulder, -90f, 60f)
        val wrist = toward(elbowP, -90f + (180f - elbow), 60f)
        return frame(
            Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to elbowP, Joint.LEFT_WRIST to wrist,
            Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle,
        )
    }

    // deitado de costas, joelho dobrado; hipAngle = tronco x coxa (ponte sobe o quadril)
    private fun lyingHip(hipAngle: Float): PoseFrame {
        val hip = Point(300f, 500f)
        val knee = toward(hip, 60f)
        val ankle = Point(knee.x + 50f, 520f)
        val shoulder = toward(hip, 60f - hipAngle)
        return frame(Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle)
    }

    // de pé dobrando o quadril (flexão do tronco), pernas retas
    private fun fold(hipAngle: Float): PoseFrame {
        val ankle = Point(300f, 600f)
        val knee = Point(300f, 500f)
        val hip = Point(300f, 400f)
        val shoulder = toward(hip, 180f - hipAngle)
        return frame(Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle)
    }

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 150): Int {
        var t = 0L
        var total = 0
        frames.forEach { total += update(it, t).counted; t += stepMs }
        return total
    }

    private fun sweep(from: Float, to: Float, steps: Int = 8) = (0..steps).map { from + (to - from) * it / steps }

    @Test
    fun flexaoNaParedeContaPeloCotovelo() {
        val t = PackTrackers.forMove(Move.WALL_PUSHUP)!!
        val rep = sweep(170f, 90f) + sweep(90f, 170f)
        assertEquals(3, t.feed((rep + rep + rep).map(::wallPush)))
    }

    @Test
    fun meiaFlexaoNaParedeNaoConta() {
        val t = PackTrackers.forMove(Move.WALL_PUSHUP)!!
        assertEquals(0, t.feed((sweep(170f, 125f) + sweep(125f, 170f)).map(::wallPush)))
    }

    @Test
    fun ponteDeGluteoContaAoSubirEDescer() {
        val t = PackTrackers.forMove(Move.GLUTE_BRIDGE)!!
        val rep = sweep(120f, 170f) + sweep(170f, 120f)
        assertEquals(2, t.feed((rep + rep).map(::lyingHip)))
    }

    @Test
    fun alongamentoSoContaSegurandoAPose() {
        val t = PackTrackers.forMove(Move.FORWARD_FOLD)!!
        var now = 0L
        // em pé (não conta) por 2 s, dobrado por 3 s
        repeat(20) { assertEquals(Cue.GO_DOWN, t.update(fold(170f), now).cue); now += 100 }
        var seconds = 0
        repeat(31) { seconds += t.update(fold(60f), now).counted; now += 100 }
        assertEquals(3, seconds)
    }

    @Test
    fun cobreOsPrincipaisMovimentos() {
        listOf(Move.PUSHUP, Move.KNEE_PUSHUP, Move.CHAIR_SQUAT, Move.GLUTE_BRIDGE, Move.SIDE_PLANK, Move.CHILD_POSE, Move.OVERHEAD_REACH)
            .forEach { assertNotNull(it.name, PackTrackers.forMove(it)) }
        assertNull(PackTrackers.forMove(Move.NECK_TILT))
        assertTrue(PackTrackers.forMove(Move.SQUAT) is SquatTracker)
    }
}
