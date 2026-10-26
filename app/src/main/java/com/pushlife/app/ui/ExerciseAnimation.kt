package com.pushlife.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.packs.Move
import com.pushlife.app.ui.theme.Orange
import com.pushlife.app.ui.theme.Yellow

// bonequinho laranja fazendo o exercício em loop
// quadro 200×200, chão em y = 180
@Composable
fun ExerciseAnimation(exercise: Exercise, modifier: Modifier = Modifier) {
    val anim = remember(exercise) { animationFor(exercise) }
    FigureAnimation(anim, modifier)
}

// mesmo bonequinho, para os movimentos dos packs
@Composable
fun MoveAnimation(move: Move, modifier: Modifier = Modifier) {
    val anim = remember(move) { animationFor(move) }
    FigureAnimation(anim, modifier)
}

@Composable
private fun FigureAnimation(anim: Anim, modifier: Modifier) {
    val n = anim.frames.size
    val transition = rememberInfiniteTransition(label = "exercise")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = n.toFloat(),
        animationSpec = infiniteRepeatable(tween(anim.segmentMs * n, easing = LinearEasing)),
        label = "pose",
    )
    val i = p.toInt().coerceIn(0, n - 1)
    val t = FastOutSlowInEasing.transform((p - i).coerceIn(0f, 1f))
    val figure = anim.frames[i].lerpTo(anim.frames[(i + 1) % n], t)
    val ground = MaterialTheme.colorScheme.outline
    val back = Color(0xFFB84A00)

    Canvas(modifier) {
        val s = minOf(size.width, size.height) / 200f
        val dx = (size.width - 200f * s) / 2f
        val dy = (size.height - 200f * s) / 2f
        fun o(p: Offset) = Offset(dx + p.x * s, dy + p.y * s)
        fun DrawScope.bone(a: Offset, b: Offset, color: Color, width: Float) =
            drawLine(color, o(a), o(b), strokeWidth = width * s, cap = StrokeCap.Round)

        // chão + parede ou barra se o exercício usar
        bone(Offset(10f, 182f), Offset(190f, 182f), ground, 3f)
        when (anim.prop) {
            Prop.WALL -> bone(Offset(48f, 20f), Offset(48f, 182f), ground, 6f)
            Prop.BAR -> bone(Offset(55f, 20f), Offset(145f, 20f), ground, 6f)
            // cadeira de lado (encosto à esquerda), banco sem encosto e banquinho visto de frente
            Prop.CHAIR, Prop.BENCH -> {
                bone(Offset(22f, 142f), Offset(68f, 142f), ground, 6f)
                bone(Offset(25f, 142f), Offset(25f, 182f), ground, 4f)
                bone(Offset(65f, 142f), Offset(65f, 182f), ground, 4f)
                if (anim.prop == Prop.CHAIR) bone(Offset(22f, 142f), Offset(22f, 96f), ground, 6f)
            }
            Prop.STOOL -> {
                bone(Offset(70f, 142f), Offset(130f, 142f), ground, 6f)
                bone(Offset(74f, 142f), Offset(74f, 182f), ground, 4f)
                bone(Offset(126f, 142f), Offset(126f, 182f), ground, 4f)
            }
            Prop.NONE -> {}
        }

        with(figure) {
            // membros de trás mais escuros (profundidade)
            bone(neck, elbowB, back, 11f); bone(elbowB, handB, back, 10f)
            bone(hip, kneeB, back, 12f); bone(kneeB, footB, back, 11f)
            // Tronco, pescoço e cabeça.
            bone(neck, hip, Orange, 17f)
            bone(neck, head, Orange, 8f)
            drawCircle(Orange, radius = 14f * s, center = o(head))
            // olho: de perfil um, de frente dois
            if (anim.facing == 0f) {
                drawCircle(Color.White, radius = 2.6f * s, center = o(head + Offset(-5f, -2f)))
                drawCircle(Color.White, radius = 2.6f * s, center = o(head + Offset(5f, -2f)))
            } else {
                drawCircle(Color.White, radius = 2.8f * s, center = o(head + Offset(6f * anim.facing, -2f)))
            }
            // pontinho amarelo da marca no peito
            drawCircle(Yellow, radius = 3.5f * s, center = o(lerp(neck, hip, 0.25f)))
            // Membros da frente.
            bone(hip, kneeF, Orange, 12f); bone(kneeF, footF, Orange, 11f)
            bone(neck, elbowF, Orange, 11f); bone(elbowF, handF, Orange, 10f)
        }
    }
}

internal enum class Prop { NONE, WALL, BAR, CHAIR, BENCH, STOOL }

internal class Anim(val frames: List<Figure>, val segmentMs: Int, val facing: Float = 1f, val prop: Prop = Prop.NONE)

// pose: F = frente, B = trás
internal data class Figure(
    val head: Offset, val neck: Offset, val hip: Offset,
    val elbowF: Offset, val handF: Offset, val elbowB: Offset, val handB: Offset,
    val kneeF: Offset, val footF: Offset, val kneeB: Offset, val footB: Offset,
) {
    fun lerpTo(o: Figure, t: Float) = Figure(
        lerp(head, o.head, t), lerp(neck, o.neck, t), lerp(hip, o.hip, t),
        lerp(elbowF, o.elbowF, t), lerp(handF, o.handF, t), lerp(elbowB, o.elbowB, t), lerp(handB, o.handB, t),
        lerp(kneeF, o.kneeF, t), lerp(footF, o.footF, t), lerp(kneeB, o.kneeB, t), lerp(footB, o.footB, t),
    )
}

internal fun p(x: Number, y: Number) = Offset(x.toFloat(), y.toFloat())

// atalho pra vista de perfil (trás atrás da frente)
private fun fig(
    head: Offset, neck: Offset, hip: Offset, elbow: Offset, hand: Offset, knee: Offset, foot: Offset,
    elbowB: Offset = elbow, handB: Offset = hand, kneeB: Offset = knee, footB: Offset = foot,
) = Figure(head, neck, hip, elbow, hand, elbowB, handB, knee, foot, kneeB, footB)

private val STAND = fig(p(100, 26), p(100, 46), p(100, 105), p(100, 76), p(102, 104), p(102, 142), p(100, 180))

internal fun animationFor(exercise: Exercise): Anim = when (exercise) {
    Exercise.PUSHUP -> Anim(
        listOf(
            fig(p(44, 98), p(60, 110), p(127, 147), p(60, 144), p(60, 180), p(155, 163), p(184, 180)),
            fig(p(43, 148), p(60, 158), p(127, 170), p(86, 166), p(60, 180), p(155, 175), p(184, 180)),
        ),
        segmentMs = 700, facing = -1f,
    )
    Exercise.SQUAT -> Anim(
        listOf(
            fig(p(100, 26), p(100, 46), p(100, 105), p(124, 52), p(148, 54), p(103, 142), p(100, 180)),
            fig(p(95, 60), p(88, 78), p(72, 132), p(112, 80), p(138, 80), p(112, 134), p(100, 180)),
        ),
        segmentMs = 800,
    )
    Exercise.LUNGE -> Anim(
        listOf(
            fig(
                p(100, 26), p(100, 46), p(100, 105), p(86, 74), p(98, 100), p(118, 141), p(136, 180),
                kneeB = p(84, 142), footB = p(66, 180),
            ),
            fig(
                p(100, 54), p(100, 74), p(100, 132), p(86, 102), p(98, 128), p(138, 134), p(138, 180),
                kneeB = p(84, 172), footB = p(56, 180),
            ),
        ),
        segmentMs = 850,
    )
    Exercise.SITUP -> Anim(
        listOf(
            fig(p(30, 168), p(48, 172), p(100, 176), p(56, 160), p(62, 168), p(132, 142), p(164, 180)),
            fig(p(92, 104), p(88, 124), p(100, 176), p(102, 136), p(104, 126), p(132, 142), p(164, 180)),
        ),
        segmentMs = 800,
    )
    Exercise.JUMPING_JACK -> Anim(
        listOf(
            Figure(
                p(100, 28), p(100, 48), p(100, 105), p(90, 78), p(86, 106), p(110, 78), p(114, 106),
                p(96, 142), p(94, 180), p(104, 142), p(106, 180),
            ),
            Figure(
                p(100, 22), p(100, 42), p(100, 100), p(76, 32), p(70, 10), p(124, 32), p(130, 10),
                p(86, 138), p(70, 180), p(114, 138), p(130, 180),
            ),
        ),
        segmentMs = 450, facing = 0f,
    )
    Exercise.PLANK -> Anim(
        listOf(
            fig(p(42, 140), p(58, 150), p(125, 156), p(58, 178), p(88, 178), p(155, 166), p(184, 180)),
            fig(p(42, 138), p(58, 148), p(125, 153), p(58, 178), p(88, 178), p(155, 165), p(184, 180)),
        ),
        segmentMs = 1500, facing = -1f,
    )
    Exercise.WALL_SIT -> Anim(
        listOf(
            fig(p(64, 52), p(60, 70), p(62, 126), p(72, 100), p(92, 118), p(112, 126), p(112, 180)),
            fig(p(64, 54), p(60, 72), p(62, 128), p(72, 102), p(92, 120), p(112, 127), p(112, 180)),
        ),
        segmentMs = 1500, prop = Prop.WALL,
    )
    Exercise.PULLUP -> Anim(
        listOf(
            Figure(
                p(100, 48), p(100, 66), p(100, 120), p(84, 42), p(80, 20), p(116, 42), p(120, 20),
                p(96, 150), p(96, 176), p(104, 150), p(104, 176),
            ),
            Figure(
                p(100, 14), p(100, 36), p(100, 92), p(70, 40), p(80, 20), p(130, 40), p(120, 20),
                p(96, 122), p(96, 150), p(104, 122), p(104, 150),
            ),
        ),
        segmentMs = 900, facing = 0f, prop = Prop.BAR,
    )
    Exercise.BURPEE -> {
        val crouch = fig(p(118, 96), p(108, 110), p(84, 138), p(122, 142), p(128, 180), p(116, 142), p(100, 180))
        val plank = fig(p(150, 102), p(134, 112), p(80, 146), p(134, 146), p(134, 180), p(52, 163), p(22, 180))
        Anim(listOf(STAND, crouch, plank, crouch), segmentMs = 550)
    }
    Exercise.WALK -> {
        val a = fig(
            p(100, 26), p(100, 46), p(100, 105), p(92, 74), p(86, 100), p(112, 141), p(124, 180),
            elbowB = p(108, 74), handB = p(116, 100), kneeB = p(92, 143), footB = p(78, 180),
        )
        val b = a.copy(elbowF = a.elbowB, handF = a.handB, elbowB = a.elbowF, handB = a.handF, kneeF = a.kneeB, footF = a.footB, kneeB = a.kneeF, footB = a.footF)
        Anim(listOf(a, b), segmentMs = 450)
    }
}
