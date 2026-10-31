package com.pushlife.app.core.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

// ponto 3D (pixels ou metros)
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun norm3d() = sqrt(x * x + y * y + z * z)
}

// só as articulações que a gente usa, com o índice no BlazePose (33 pontos)
enum class Joint(val index: Int) {
    NOSE(0),
    LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_ELBOW(13), RIGHT_ELBOW(14),
    LEFT_WRIST(15), RIGHT_WRIST(16),
    LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26),
    LEFT_ANKLE(27), RIGHT_ANKLE(28),
}

// ponto na imagem (pixels, y pra baixo) + chance de estar visível
// world = mesmo ponto em 3D (metros), com ele o ângulo sai certo de qualquer lado
data class Point(val x: Float, val y: Float, val visibility: Float = 1f, val world: Vec3? = null)

// articulações de um lado do corpo
data class Side(
    val shoulder: Point,
    val elbow: Point,
    val wrist: Point,
    val hip: Point,
    val knee: Point,
    val ankle: Point,
)

// pose de um quadro, só geometria (sem Android); all = todos os pontos 3D do detector
class PoseFrame(
    private val points: Map<Joint, Point>,
    private val minVisibility: Float = 0.5f,
    val all: List<Vec3> = emptyList(),
) {

    operator fun get(joint: Joint): Point? = points[joint]?.takeIf { it.visibility >= minVisibility }

    // pega o lado mais visível que tenha tudo que precisa (de lado, é o mais perto da câmera)
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

    // um lado específico (esquerdo ou direito), se as partes pedidas estiverem visíveis
    fun side(left: Boolean, vararg needed: SidePart): Side? {
        val (side, visibilityOf) = sideOf(left)
        val score = needed.minOfOrNull { visibilityOf(it) } ?: 1f
        return side?.takeIf { score >= minVisibility }
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
    // ângulo em b (graus); 3D quando dá, porque de frente a imagem plana não vê o cotovelo dobrar
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

    // inclinação em 3D: 0 = em pé, 90 = deitado
    fun leanFromVertical3d(from: Vec3, to: Vec3): Float {
        val d = to - from
        return Math.toDegrees(atan2(hypot(d.x, d.z).toDouble(), abs(d.y).toDouble())).toFloat()
    }

    // mesma coisa em 2D
    fun leanFromVertical(from: Point, to: Point): Float =
        Math.toDegrees(atan2(abs(to.x - from.x).toDouble(), abs(to.y - from.y).toDouble())).toFloat()

    // p abaixo da reta a-b = positivo, acima = negativo
    fun belowLine(p: Point, a: Point, b: Point): Float {
        if (a.x == b.x) return 0f
        val t = (p.x - a.x) / (b.x - a.x)
        val lineY = a.y + t * (b.y - a.y)
        return p.y - lineY
    }
}
