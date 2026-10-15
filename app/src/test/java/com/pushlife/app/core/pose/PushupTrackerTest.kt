package com.pushlife.app.core.pose

import com.pushlife.app.core.pose.classify.PoseClassifier
import com.pushlife.app.core.pose.classify.PoseSample
import com.pushlife.app.core.pose.classify.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Testa a flexão com as poses de referência do Google (assets/pose/fitness_pose_samples.csv). */
class PushupTrackerTest {
    private val lines: List<String> = File("src/main/assets/pose/fitness_pose_samples.csv").readLines()

    private fun landmarks(line: String): List<Vec3> {
        val t = line.split(',')
        return (0 until 33).map { i -> Vec3(t[2 + i * 3].toFloat(), t[3 + i * 3].toFloat(), t[4 + i * 3].toFloat()) }
    }

    private fun linesOf(cls: String) = lines.filter { it.split(',')[1] == cls }

    private fun frame(lm: List<Vec3>) = PoseFrame(emptyMap(), all = lm)

    private fun PoseTracker.feed(frames: List<PoseFrame>, stepMs: Long = 66): List<TrackerUpdate> {
        var t = 0L
        return frames.map { f -> update(f, t).also { t += stepMs } }
    }

    @Test
    fun classificaPosesQueNaoViuAntes() {
        // Separa 1 de cada 5 fotos para testar e usa o resto como referência.
        val (test, train) = lines.withIndex().partition { it.index % 5 == 0 }
        val classifier = PoseClassifier(PoseSample.parseAll(train.asSequence().map { it.value }))
        val pushups = test.map { it.value }.filter { it.split(',')[1].startsWith("pushups") }
        val right = pushups.count { line ->
            val result = classifier.classify(landmarks(line))
            result.maxByOrNull { it.value }?.key == line.split(',')[1]
        }
        assertTrue("acertou $right de ${pushups.size}", right >= pushups.size * 0.9)
    }

    @Test
    fun contaFlexoesSubindoEDescendo() {
        val classifier = PoseClassifier(PoseSample.parseAll(lines.asSequence()))
        val ups = linesOf(PushupTracker.UP).take(5).map(::landmarks)
        val downs = linesOf(PushupTracker.DOWN).take(5).map(::landmarks)
        val tr = PushupTracker { classifier }
        val frames = (0 until 4).flatMap { i ->
            List(12) { frame(ups[i]) } + List(12) { frame(downs[i]) }
        } + List(12) { frame(ups[4]) }
        tr.feed(frames)
        assertEquals(4, tr.count)
    }

    @Test
    fun mexerSoACabecaNaoConta() {
        val classifier = PoseClassifier(PoseSample.parseAll(lines.asSequence()))
        val up = landmarks(linesOf(PushupTracker.UP).first())
        val tr = PushupTracker { classifier }
        // Braços esticados o tempo todo; só os pontos do rosto (0 a 10) sobem e descem bastante.
        val frames = List(120) { i ->
            val dy = if ((i / 10) % 2 == 0) 0f else 120f
            frame(up.mapIndexed { j, p -> if (j <= 10) Vec3(p.x, p.y + dy, p.z) else p })
        }
        tr.feed(frames)
        assertEquals(0, tr.count)
    }

    @Test
    fun agachamentoEmPeNaoContaFlexao() {
        val classifier = PoseClassifier(PoseSample.parseAll(lines.asSequence()))
        val ups = linesOf("squats_up").take(4).map(::landmarks)
        val downs = linesOf("squats_down").take(4).map(::landmarks)
        val tr = PushupTracker { classifier }
        tr.feed((0 until 4).flatMap { i -> List(12) { frame(ups[i]) } + List(12) { frame(downs[i]) } })
        assertEquals(0, tr.count)
    }

    @Test
    fun modeloLentoAindaConta() {
        // ~3 quadros por segundo (modelo preciso num celular simples).
        val classifier = PoseClassifier(PoseSample.parseAll(lines.asSequence()))
        val ups = linesOf(PushupTracker.UP).take(4).map(::landmarks)
        val downs = linesOf(PushupTracker.DOWN).take(4).map(::landmarks)
        val tr = PushupTracker { classifier }
        val frames = (0 until 3).flatMap { i -> List(5) { frame(ups[i]) } + List(5) { frame(downs[i]) } } + List(5) { frame(ups[3]) }
        tr.feed(frames, stepMs = 330)
        assertEquals(3, tr.count)
    }

    @Test
    fun semPoseCarregadaEsperaSemContar() {
        val tr = PushupTracker { null }
        val up = tr.update(frame(landmarks(lines.first())), 0)
        assertEquals(Cue.GET_READY, up.cue)
        assertEquals(0, tr.count)
    }
}
