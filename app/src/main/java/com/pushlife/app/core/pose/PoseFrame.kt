package com.pushlife.app.core.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** Um ponto 3D (x e y em pixels com z na mesma escala, ou em metros). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun norm3d() = sqrt(x * x + y * y + z * z)
}

/** Articulações que os exercícios usam (subconjunto dos 33 pontos do BlazePose) e o índice de cada uma. */
enum class Joint(val index: Int) {
    NOSE(0),
    LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_ELBOW(13), RIGHT_ELBOW(14),
    LEFT_WRIST(15), RIGHT_WRIST(16),
    LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26),
    LEFT_ANKLE(27), RIGHT_ANKLE(28),
}

/**
 * Um ponto na imagem (em pixels, y cresce para baixo) e a chance de ele estar mesmo visível.
 * [world] é o mesmo ponto em 3D, em metros (y para baixo), quando o detector sabe a profundidade.
 * Com ele os ângulos saem certos de qualquer lado que a câmera esteja.
 */
data class Point(val x: Float, val y: Float, val visibility: Float = 1f, val world: Vec3? = null)

/** As articulações de um lado do corpo. */
data class Side(
    val shoulder: Point,
    val elbow: Point,
    val wrist: Point,
    val hip: Point,
    val knee: Point,
    val ankle: Point,
)

/**
 * Uma pose detectada num quadro da câmera. Tudo aqui é geometria pura, sem Android.
 * [all] são todos os marcos 3D que o detector deu, na ordem dele.
 */
class PoseFrame(
    private val points: Map<Joint, Point>,
    private val minVisibility: Float = 0.5f,
    val all: List<Vec3> = emptyList(),
) {

    operator fun get(joint: Joint): Point? = points[joint]?.takeIf { it.visibility >= minVisibility }

    /**
     * O lado do corpo mais visível que tenha todas as [needed] articulações.
     * Com a pessoa de lado para a câmera, o lado mais perto é o que importa.
     */
    fun bestSide(vararg needed: SidePart): Side? {
        val sides = listOf(sideOf(left = true), sideOf(left = false))
        return sides
            .mapNotNull { (side, visibilityOf) ->
                val score = needed.minOfOrNull { visibilityOf(it) } ?: 1f
                if (side != null && score >= minVisibility) side to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun sideOf(left: Boolean): Pair<Side?, (SidePart) -> Float> {
        fun j(l: Joint, r: Joint) = points[if (left) l else r]
        val parts = mapOf(
            SidePart.SHOULDER to j(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER),
            SidePart.ELBOW to j(Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW),
            SidePart.WRIST to j(Joint.LEFT_WRIST, Joint.RIGHT_WRIST),
            SidePart.HIP to j(Joint.LEFT_HIP, Joint.RIGHT_HIP),
            SidePart.KNEE to j(Joint.LEFT_KNEE, Joint.RIGHT_KNEE),
            SidePart.ANKLE to j(Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE),
        )
        val side = if (parts.values.all { it != null }) {
            Side(
                parts.getValue(SidePart.SHOULDER)!!, parts.getValue(SidePart.ELBOW)!!, parts.getValue(SidePart.WRIST)!!,
                parts.getValue(SidePart.HIP)!!, parts.getValue(SidePart.KNEE)!!, parts.getValue(SidePart.ANKLE)!!,
            )
        } else {
            null
        }
        return side to { part: SidePart -> parts[part]?.visibility ?: 0f }
    }
}

enum class SidePart { SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE }

object Geometry {
    /**
     * Ângulo (graus) no vértice [b] formado por a-b-c. Em 3D quando os três pontos têm [Point.world]
     * (de frente para a câmera o cotovelo dobra "para dentro" da tela e a imagem plana não vê).
     */
    fun angle(a: Point, b: Point, c: Point): Float {
        val aw = a.world
        val bw = b.world
        val cw = c.world
        if (aw != null && bw != null && cw != null) return angle3d(aw, bw, cw)
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val mag = hypot(abx, aby) * hypot(cbx, cby)
        if (mag == 0f) return 180f
        val cos = ((abx * cbx + aby * cby) / mag).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    fun angle3d(a: Vec3, b: Vec3, c: Vec3): Float {
        val ab = a - b
        val cb = c - b
        val mag = ab.norm3d() * cb.norm3d()
        if (mag == 0f) return 180f
        val cos = ((ab.x * cb.x + ab.y * cb.y + ab.z * cb.z) / mag).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** Inclinação em 3D do segmento [from]→[to] em relação à vertical: 0 = em pé, 90 = deitado. */
    fun leanFromVertical3d(from: Vec3, to: Vec3): Float {
        val d = to - from
        return Math.toDegrees(atan2(hypot(d.x, d.z).toDouble(), abs(d.y).toDouble())).toFloat()
    }

    /** Inclinação do segmento [from]→[to] em relação à vertical: 0 = em pé, 90 = deitado. */
    fun leanFromVertical(from: Point, to: Point): Float =
        Math.toDegrees(atan2(abs(to.x - from.x).toDouble(), abs(to.y - from.y).toDouble())).toFloat()

    /** Quanto [p] está abaixo (positivo) ou acima (negativo) da reta a-b, na vertical. */
    fun belowLine(p: Point, a: Point, b: Point): Float {
        if (a.x == b.x) return 0f
        val t = (p.x - a.x) / (b.x - a.x)
        val lineY = a.y + t * (b.y - a.y)
        return p.y - lineY
    }
}
