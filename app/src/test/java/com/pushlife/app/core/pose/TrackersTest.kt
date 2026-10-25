package com.pushlife.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

// poses de mentira (lado esquerdo, de perfil) montadas pelos ângulos
private object Poses {
    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    // anda len px a partir do ponto; 0° = pra cima, 90° = pra direita
    fun toward(from: Point, deg: Float, len: Float = 100f) =
        Point(from.x + (len * sin(rad(deg))).toFloat(), from.y - (len * cos(rad(deg))).toFloat())

    fun side(shoulder: Point, elbow: Point, wrist: Point, hip: Point, knee: Point, ankle: Point) = PoseFrame(
        mapOf(
            Joint.LEFT_SHOULDER to shoulder, Joint.LEFT_ELBOW to elbow, Joint.LEFT_WRIST to wrist,
            Joint.LEFT_HIP to hip, Joint.LEFT_KNEE to knee, Joint.LEFT_ANKLE to ankle,
        ),
    )

    // agachamento de lado: ângulo do joelho + inclinação do tronco
    fun squat(kneeAngle: Float, lean: Float = 10f): PoseFrame {
        val ankle = Point(300f, 600f)
        val knee = Point(300f, 500f)
        // joelho → quadril: sobe e vai girando pra trás conforme dobra
        val hip = toward(knee, -(180f - kneeAngle))
        val shoulder = toward(hip, lean)
        return side(shoulder, toward(shoulder, 180f, 60f), toward(shoulder, 180f, 120f), hip, knee, ankle)
    }

    // abdominal deitado com joelho dobrado; hipAngle = tronco x coxa
    fun situp(hipAngle: Float): PoseFrame {
        val hip = Point(300f, 500f)
        val knee = toward(hip, 60f) // coxa subindo para a direita
        val ankle = Point(knee.x + 60f, 500f)
        val shoulder = toward(hip, 60f - hipAngle) // gira o tronco a partir da coxa
        return side(shoulder, toward(shoulder, 180f, 50f), toward(shoulder, 180f, 90f), hip, knee, ankle)
    }

    // barra fixa, só muda o cotovelo
    fun pullup(elbowAngle: Float): PoseFrame {
        val shoulder = Point(300f, 300f)
        val elbow = toward(shoulder, 0f)
        val wrist = toward(elbow, -(180f - elbowAngle))
        val hip = toward(shoulder, 180f, 150f)
        val knee = toward(hip, 180f)
        return side(shoulder, elbow, wrist, hip, knee, toward(knee, 180f))
    }

    // corpo todo inclinado: 0 = em pé, 90 = deitado
    fun torso(lean: Float, hipSag: Float = 0f): PoseFrame {
        val hip = Point(300f, 400f + hipSag)
        val shoulder = toward(Point(300f, 400f), lean, 120f)
        val ankle = toward(Point(300f, 400f), lean + 180f, 200f)
        val knee = Point((hip.x + ankle.x) / 2, (hip.y + ankle.y) / 2)
        return side(shoulder, toward(shoulder, 180f, 60f), toward(shoulder, 180f, 120f), hip, knee, ankle)
    }

    // polichinelo de frente
    fun jack(open: Boolean): PoseFrame {
        val ls = Point(260f, 200f)
        val rs = Point(340f, 200f)
        val handY = if (open) 80f else 350f
        val footSpread = if (open) 70f else 20f
        return PoseFrame(
            mapOf(
                Joint.LEFT_SHOULDER to ls, Joint.RIGHT_SHOULDER to rs,
                Joint.LEFT_WRIST to Point(220f, handY), Joint.RIGHT_WRIST to Point(380f, handY),
                Joint.LEFT_ANKLE to Point(300f - footSpread, 600f), Joint.RIGHT_ANKLE to Point(300f + footSpread, 600f),
            ),
        )
    }
}

class GeometryTest {
    @Test
    fun anguloReto() {
        assertEquals(90f, Geometry.angle(Point(0f, -1f), Point(0f, 0f), Point(1f, 0f)), 0.01f)
    }

    @Test
    fun posesSinteticasTemOAnguloPedido() {
        for (a in listOf(80f, 120f, 170f)) {
            val side = Poses.squat(a).bestSide(SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)!!
            assertEquals(a, Geometry.angle(side.hip, side.knee, side.ankle), 0.5f)
        }
        val s = Poses.situp(70f).bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE)!!
        assertEquals(70f, Geometry.angle(s.shoulder, s.hip, s.knee), 0.5f)
        val p = Poses.pullup(60f).bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)!!
        assertEquals(60f, Geometry.angle(p.shoulder, p.elbow, p.wrist), 0.5f)
    }

    @Test
    fun inclinacaoDoTronco() {
        val t = Poses.torso(80f).bestSide(SidePart.SHOULDER, SidePart.HIP)!!
        assertEquals(80f, Geometry.leanFromVertical(t.hip, t.shoulder), 0.5f)
    }
}

class TrackersTest {
    private fun PoseTracker.run(frames: List<PoseFrame?>, start: Long = 0, stepMs: Long = 50): Pair<Long, List<TrackerUpdate>> {
        var t = start
        val out = frames.map { f -> update(f, t).also { t += stepMs } }
        return t to out
    }

    private fun sweep(from: Float, to: Float, steps: Int = 10) = (0..steps).map { from + (to - from) * it / steps }

    @Test
    fun agachamentoCompletoConta() {
        val tr = SquatTracker()
        val angles = sweep(170f, 170f, 3) + sweep(170f, 80f) + sweep(80f, 170f) + sweep(170f, 80f) + sweep(80f, 170f)
        tr.run(angles.map { Poses.squat(it) })
        assertEquals(2, tr.count)
    }

    @Test
    fun meioAgachamentoAvisaDescaMais() {
        val tr = SquatTracker()
        val angles = sweep(170f, 170f, 3) + sweep(170f, 125f) + sweep(125f, 170f)
        val (_, ups) = tr.run(angles.map { Poses.squat(it) })
        assertEquals(0, tr.count)
        assertTrue(ups.any { it.cue == Cue.GO_LOWER })
    }

    @Test
    fun troncoMuitoInclinadoNoFundoAvisaPeito() {
        val tr = SquatTracker()
        val (_, ups) = tr.run((sweep(170f, 170f, 3) + sweep(170f, 80f)).map { Poses.squat(it, lean = 70f) })
        assertEquals(Cue.CHEST_UP, ups.last().cue)
    }

    @Test
    fun semPessoaPedeParaAparecer() {
        assertEquals(Cue.NOT_VISIBLE, SquatTracker().update(null, 0).cue)
    }

    @Test
    fun abdominalConta() {
        val tr = SitupTracker()
        val angles = sweep(130f, 130f, 3) + sweep(130f, 60f) + sweep(60f, 130f)
        tr.run(angles.map { Poses.situp(it) })
        assertEquals(1, tr.count)
    }

    @Test
    fun barraFixaConta() {
        val tr = PullupTracker()
        val angles = sweep(170f, 170f, 3) + sweep(170f, 50f) + sweep(50f, 170f)
        tr.run(angles.map { Poses.pullup(it) })
        assertEquals(1, tr.count)
    }

    @Test
    fun polichineloContaCiclosEIgnoraTremida() {
        val tr = JumpingJackTracker()
        val c = Poses.jack(open = false)
        val o = Poses.jack(open = true)
        // fecha/abre/fecha/abre/fecha = 2; quadro aberto solto no meio não conta
        tr.run(listOf(c, c, c, o, o, o, c, c, c, o, c, c, o, o, o, c, c, c), stepMs = 200)
        assertEquals(2, tr.count)
    }

    @Test
    fun burpeeContaDoChaoDeVoltaAoEmPe() {
        val tr = BurpeeTracker()
        val frames = List(3) { Poses.torso(5f) } + List(3) { Poses.torso(80f) } + List(3) { Poses.torso(5f) }
        tr.run(frames, stepMs = 300)
        assertEquals(1, tr.count)
    }

    @Test
    fun pranchaContaSegundosSoComCorpoAlinhado() {
        val tr = PlankTracker()
        val (_, ups) = tr.run(List(51) { Poses.torso(80f) }, stepMs = 100) // 5 s alinhado
        assertEquals(5, tr.count)
        assertEquals(5, ups.sumOf { it.counted })
        assertEquals(Cue.HOLD, ups.last().cue)
    }

    @Test
    fun pranchaComQuadrilCaidoPedeParaSubir() {
        val tr = PlankTracker()
        val (_, ups) = tr.run(List(10) { Poses.torso(80f, hipSag = 40f) }, stepMs = 100)
        assertEquals(0, tr.count)
        assertEquals(Cue.HIPS_UP, ups.last().cue)
    }

    // --- Cada exercício só conta com o corpo na posição certa ---

    @Test
    fun agachamentoDeitadoNaoConta() {
        val tr = SquatTracker()
        // Tronco a 85° (deitado) dobrando o joelho: não é agachamento.
        val angles = sweep(170f, 170f, 3) + sweep(170f, 80f) + sweep(80f, 170f) + sweep(170f, 80f) + sweep(80f, 170f)
        val (_, ups) = tr.run(angles.map { Poses.squat(it, lean = 85f) })
        assertEquals(0, tr.count)
        assertEquals(Cue.STAND, ups.last().cue)
    }

    @Test
    fun emPeDobrandoOTroncoNaoContaAbdominal() {
        val tr = SitupTracker()
        // em pé, só dobrando o tronco pra frente até 60° e voltando
        val hip = Point(300f, 400f)
        val knee = Poses.toward(hip, 180f)
        val ankle = Poses.toward(knee, 180f)
        fun bend(trunk: Float): PoseFrame {
            val shoulder = Poses.toward(hip, 180f - trunk) // trunk = ângulo ombro-quadril-joelho
            return Poses.side(shoulder, Poses.toward(shoulder, 180f, 50f), Poses.toward(shoulder, 180f, 90f), hip, knee, ankle)
        }
        val angles = sweep(170f, 170f, 3) + sweep(170f, 60f) + sweep(60f, 170f) + sweep(170f, 60f) + sweep(60f, 170f)
        val (_, ups) = tr.run(angles.map { bend(it) })
        assertEquals(0, tr.count)
        assertEquals(Cue.FLOOR, ups.last().cue)
    }

    @Test
    fun roscaComAsMaosEmbaixoNaoContaBarra() {
        val tr = PullupTracker()
        // rosca em pé: dobra o cotovelo com as mãos abaixo dos ombros
        fun curl(elbowAngle: Float): PoseFrame {
            val shoulder = Point(300f, 300f)
            val elbow = Poses.toward(shoulder, 180f)
            val wrist = Poses.toward(elbow, 180f - (180f - elbowAngle))
            val hip = Poses.toward(shoulder, 180f, 150f)
            val knee = Poses.toward(hip, 180f)
            return Poses.side(shoulder, elbow, wrist, hip, knee, Poses.toward(knee, 180f))
        }
        // dobra até 50° igual na barra, mas com as mãos abaixo dos ombros
        val angles = sweep(170f, 170f, 3) + sweep(170f, 50f) + sweep(50f, 170f)
        tr.run(angles.map { curl(it) })
        assertEquals(0, tr.count)
    }

    @Test
    fun pranchaDeJoelhosNaoContaTempo() {
        val tr = PlankTracker()
        // deitado mas com o joelho no chão (perna a ~90°)
        val shoulder = Point(200f, 300f)
        val hip = Point(380f, 330f)
        val knee = Point(480f, 400f)
        val ankle = Point(560f, 330f)
        val f = Poses.side(shoulder, Poses.toward(shoulder, 180f, 60f), Poses.toward(shoulder, 180f, 120f), hip, knee, ankle)
        val (_, ups) = tr.run(List(60) { f }, stepMs = 100)
        assertEquals(0, tr.count)
        assertEquals(Cue.KNEES_UP, ups.last().cue)
    }
}
