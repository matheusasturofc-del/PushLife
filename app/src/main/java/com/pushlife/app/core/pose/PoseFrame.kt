package com.pushlife.app.core.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** Articulações que os exercícios usam (subconjunto dos 33 pontos do ML Kit Pose). */
enum class Joint {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

/** Um ponto na imagem (em pixels, y cresce para baixo) e a chance de ele estar mesmo visível. */
data class Point(val x: Float, val y: Float, val visibility: Float = 1f)

/** As articulações de um lado do corpo. */
data class Side(
    val shoulder: Point,
    val elbow: Point,
    val wrist: Point,
    val hip: Point,
    val knee: Point,
    val ankle: Point,
)

/** Uma pose detectada num quadro da câmera. Tudo aqui é geometria pura, sem Android. */
class PoseFrame(private val points: Map<Joint, Point>, private val minVisibility: Float = 0.5f) {

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
    /** Ângulo (graus) no vértice [b] formado por a-b-c. */
    fun angle(a: Point, b: Point, c: Point): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val mag = hypot(abx, aby) * hypot(cbx, cby)
        if (mag == 0f) return 180f
        val cos = ((abx * cbx + aby * cby) / mag).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
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
