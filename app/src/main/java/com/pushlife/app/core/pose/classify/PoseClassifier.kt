// Adaptado do exemplo oficial do Google ML Kit (googlesamples/mlkit, vision-quickstart,
// posedetector/classification), licença Apache 2.0 — veja licenses/mlkit-samples-LICENSE.txt.
// As poses de referência (assets/pose/fitness_pose_samples.csv) vêm do mesmo exemplo.
package com.pushlife.app.core.pose.classify

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Ponto 3D de um dos 33 marcos do ML Kit Pose (x e y em pixels, z em escala parecida). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    fun scaled(w: Vec3) = Vec3(x * w.x, y * w.y, z * w.z)
    fun maxAbs() = max(abs(x), max(abs(y), abs(z)))
    fun sumAbs() = abs(x) + abs(y) + abs(z)
    fun norm2d() = hypot(x, y)
}

private fun avg(a: Vec3, b: Vec3) = Vec3((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)

/** Índices dos marcos do ML Kit Pose que o classificador usa. */
private object Lm {
    const val COUNT = 33
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28
}

/**
 * Transforma a pose em distâncias entre articulações, sem depender de onde a pessoa está na imagem
 * nem do tamanho dela (centraliza no quadril e divide pelo tamanho do corpo).
 */
object PoseEmbedding {
    private const val TORSO_MULTIPLIER = 2.5f

    fun of(landmarks: List<Vec3>): List<Vec3> = embedding(normalize(landmarks))

    private fun normalize(lm: List<Vec3>): List<Vec3> {
        val center = avg(lm[Lm.L_HIP], lm[Lm.R_HIP])
        val centered = lm.map { it - center }
        val size = poseSize(centered)
        return centered.map { it * (100f / size) }
    }

    private fun poseSize(lm: List<Vec3>): Float {
        val hips = avg(lm[Lm.L_HIP], lm[Lm.R_HIP])
        val shoulders = avg(lm[Lm.L_SHOULDER], lm[Lm.R_SHOULDER])
        var maxDistance = (hips - shoulders).norm2d() * TORSO_MULTIPLIER
        for (p in lm) maxDistance = max(maxDistance, (p - hips).norm2d())
        return maxDistance.coerceAtLeast(1e-3f)
    }

    private fun embedding(lm: List<Vec3>): List<Vec3> {
        fun d(a: Int, b: Int) = lm[b] - lm[a]
        return listOf(
            // Uma articulação.
            avg(lm[Lm.L_SHOULDER], lm[Lm.R_SHOULDER]) - avg(lm[Lm.L_HIP], lm[Lm.R_HIP]),
            d(Lm.L_SHOULDER, Lm.L_ELBOW), d(Lm.R_SHOULDER, Lm.R_ELBOW),
            d(Lm.L_ELBOW, Lm.L_WRIST), d(Lm.R_ELBOW, Lm.R_WRIST),
            d(Lm.L_HIP, Lm.L_KNEE), d(Lm.R_HIP, Lm.R_KNEE),
            d(Lm.L_KNEE, Lm.L_ANKLE), d(Lm.R_KNEE, Lm.R_ANKLE),
            // Duas articulações.
            d(Lm.L_SHOULDER, Lm.L_WRIST), d(Lm.R_SHOULDER, Lm.R_WRIST),
            d(Lm.L_HIP, Lm.L_ANKLE), d(Lm.R_HIP, Lm.R_ANKLE),
            // Quatro articulações.
            d(Lm.L_HIP, Lm.L_WRIST), d(Lm.R_HIP, Lm.R_WRIST),
            // Cinco articulações.
            d(Lm.L_SHOULDER, Lm.L_ANKLE), d(Lm.R_SHOULDER, Lm.R_ANKLE),
            d(Lm.L_HIP, Lm.L_WRIST), d(Lm.R_HIP, Lm.R_WRIST),
            // Atravessando o corpo.
            d(Lm.L_ELBOW, Lm.R_ELBOW), d(Lm.L_KNEE, Lm.R_KNEE),
            d(Lm.L_WRIST, Lm.R_WRIST), d(Lm.L_ANKLE, Lm.R_ANKLE),
        )
    }
}

/** Uma pose de referência: nome da foto, classe (ex.: "pushups_down") e a pose já transformada. */
class PoseSample(val name: String, val className: String, val embedding: List<Vec3>) {
    companion object {
        /** Linha do CSV: nome,classe,x1,y1,z1,...,x33,y33,z33. Linha inválida → null. */
        fun parse(line: String): PoseSample? {
            val t = line.split(',')
            if (t.size != Lm.COUNT * 3 + 2) return null
            val landmarks = (0 until Lm.COUNT).map { i ->
                val x = t[2 + i * 3].toFloatOrNull() ?: return null
                val y = t[3 + i * 3].toFloatOrNull() ?: return null
                val z = t[4 + i * 3].toFloatOrNull() ?: return null
                Vec3(x, y, z)
            }
            return PoseSample(t[0], t[1], PoseEmbedding.of(landmarks))
        }

        fun parseAll(lines: Sequence<String>): List<PoseSample> = lines.mapNotNull(::parse).toList()
    }
}

/**
 * k-vizinhos mais próximos: compara a pose com todas as de referência e devolve, para cada classe,
 * quantas das [meanTopK] mais parecidas são daquela classe (0..[meanTopK]). Também testa a pose
 * espelhada, então tanto faz o lado do corpo virado para a câmera.
 */
class PoseClassifier(
    private val samples: List<PoseSample>,
    private val maxTopK: Int = 30,
    private val meanTopK: Int = 10,
    private val weights: Vec3 = Vec3(1f, 1f, 0.2f), // z é menos confiável
) {
    val confidenceRange: Int get() = min(maxTopK, meanTopK)

    fun classify(landmarks: List<Vec3>): Map<String, Float> {
        if (landmarks.size < Lm.COUNT || samples.isEmpty()) return emptyMap()
        val embedding = PoseEmbedding.of(landmarks)
        val flipped = PoseEmbedding.of(landmarks.map { Vec3(-it.x, it.y, it.z) })

        // 1ª etapa: as maxTopK mais parecidas pela MAIOR diferença (tira poses com uma articulação bem diferente).
        val byMax = samples.map { s ->
            var original = 0f
            var mirror = 0f
            for (i in embedding.indices) {
                original = max(original, (embedding[i] - s.embedding[i]).scaled(weights).maxAbs())
                mirror = max(mirror, (flipped[i] - s.embedding[i]).scaled(weights).maxAbs())
            }
            s to min(original, mirror)
        }.sortedBy { it.second }.take(maxTopK)

        // 2ª etapa: dessas, as meanTopK mais parecidas pela diferença MÉDIA.
        val byMean = byMax.map { (s, _) ->
            var original = 0f
            var mirror = 0f
            for (i in embedding.indices) {
                original += (embedding[i] - s.embedding[i]).scaled(weights).sumAbs()
                mirror += (flipped[i] - s.embedding[i]).scaled(weights).sumAbs()
            }
            s to min(original, mirror) / (embedding.size * 2)
        }.sortedBy { it.second }.take(meanTopK)

        return byMean.groupingBy { it.first.className }.eachCount().mapValues { it.value.toFloat() }
    }
}

/**
 * Suaviza a classificação ao longo dos últimos quadros (média móvel exponencial), para uma pose
 * tremida não contar repetição. Usa no máximo [window] quadros e só os de até [maxAgeMs] atrás,
 * para responder rápido mesmo em celulares que processam poucos quadros por segundo.
 * Se passar [resetMs] sem quadro, começa de novo.
 */
class EmaSmoothing(
    private val window: Int = 10,
    private val alpha: Float = 0.2f,
    private val resetMs: Long = 400,
    private val maxAgeMs: Long = 1_000,
) {
    private val history = ArrayDeque<Pair<Long, Map<String, Float>>>()
    private var lastMs = Long.MIN_VALUE / 2

    fun smooth(result: Map<String, Float>, nowMs: Long): Map<String, Float> {
        if (nowMs - lastMs > resetMs) history.clear()
        lastMs = nowMs
        history.addFirst(nowMs to result)
        while (history.size > window || (history.size > 1 && nowMs - history.last().first > maxAgeMs)) history.removeLast()
        val classes = history.flatMap { it.second.keys }.toSet()
        return classes.associateWith { c ->
            var factor = 1f
            var top = 0f
            var bottom = 0f
            for ((_, r) in history) {
                top += factor * (r[c] ?: 0f)
                bottom += factor
                factor *= 1f - alpha
            }
            top / bottom
        }
    }
}

/** Conta uma repetição quando a confiança na pose [className] passa de [enter] e depois cai abaixo de [exit]. */
class RepetitionCounter(private val className: String, private val enter: Float = 6f, private val exit: Float = 4f) {
    var count = 0
        private set
    var inPose = false
        private set

    /** @return true quando completou uma repetição neste quadro. */
    fun add(result: Map<String, Float>): Boolean {
        val confidence = result[className] ?: 0f
        if (!inPose) {
            inPose = confidence > enter
            return false
        }
        if (confidence < exit) {
            count++
            inPose = false
            return true
        }
        return false
    }

    fun reset() {
        inPose = false
    }
}
