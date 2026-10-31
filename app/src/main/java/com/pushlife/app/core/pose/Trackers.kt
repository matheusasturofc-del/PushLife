package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import kotlin.math.exp
import kotlin.math.hypot

// dicas do treino (os textos ficam no strings.xml)
enum class Cue {
    NOT_VISIBLE, GET_READY,
    GO_DOWN, GO_UP, GO_LOWER,
    CHEST_UP, HIPS_UP, HIPS_DOWN, HOLD, KNEES_UP, TURN_SIDEWAYS,
    OPEN, CLOSE, FLOOR, STAND, PULL, EXTEND,
}

// counted = reps (ou segundos na prancha) nesse quadro, progress = barra 0..1
// debug = o que o contador tá vendo, aparece pequenininho na tela
data class TrackerUpdate(val counted: Int, val progress: Float, val cue: Cue, val debug: String? = null)

// cada exercício tem o seu, recebe a pose quadro a quadro
interface PoseTracker {
    val count: Int
    fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate

    companion object {
        fun forExercise(exercise: Exercise): PoseTracker = when (exercise) {
            Exercise.PUSHUP -> PushupTracker()
            Exercise.SQUAT -> SquatTracker()
            Exercise.LUNGE -> LungeTracker()
            Exercise.WALL_SIT -> WallSitTracker()
            Exercise.SITUP -> SitupTracker()
            Exercise.PULLUP -> PullupTracker()
            Exercise.JUMPING_JACK -> JumpingJackTracker()
            Exercise.BURPEE -> BurpeeTracker()
            Exercise.PLANK -> PlankTracker()
            else -> error("$exercise não usa a câmera de pose")
        }
    }
}

// conta reps por um ângulo que fecha embaixo (joelho, quadril, cotovelo)
// passou de partial mas não chegou em contracted = meia rep, não conta e avisa "Desça mais!"
class AngleRepCounter(
    private val contracted: Float,
    private val extended: Float,
    private val partial: Float,
    private val smoothMs: Long = SMOOTH_MS,
    private val minRepMs: Long = 500,
) {
    enum class Phase { WAITING, EXTENDED, CONTRACTED }

    data class Result(val counted: Boolean, val partial: Boolean)

    var count = 0
        private set
    var phase = Phase.WAITING
        private set
    var angle: Float? = null
        private set

    private var lowest = Float.MAX_VALUE
    private var lastRepAt = Long.MIN_VALUE / 2
    private var angleAt = 0L

    val progress: Float
        get() = angle?.let { ((extended - it) / (extended - contracted)).coerceIn(0f, 1f) } ?: 0f

    // esquece a rep em andamento (ex.: saiu da posição)
    fun reset() {
        phase = Phase.WAITING
        angle = null
        lowest = Float.MAX_VALUE
    }

    fun update(raw: Float?, timeMs: Long): Result {
        if (raw == null) return Result(counted = false, partial = false)
        val a = angle?.let { it + smoothFactor(timeMs - angleAt, smoothMs) * (raw - it) } ?: raw
        angle = a
        angleAt = timeMs
        var counted = false
        var half = false
        when (phase) {
            Phase.WAITING -> if (a >= extended) {
                phase = Phase.EXTENDED
                lowest = a
            }
            Phase.EXTENDED -> {
                lowest = minOf(lowest, a)
                if (a <= contracted) {
                    phase = Phase.CONTRACTED
                } else if (a >= extended && lowest <= partial) {
                    half = true // voltou sem chegar ao fundo
                    lowest = a
                }
            }
            Phase.CONTRACTED -> if (a >= extended) {
                phase = Phase.EXTENDED
                lowest = a
                if (timeMs - lastRepAt >= minRepMs) {
                    count++
                    lastRepAt = timeMs
                    counted = true
                }
            }
        }
        return Result(counted, half)
    }
}

// suavização dos ângulos
const val SMOOTH_MS = 70L

// peso do quadro novo vai pelo tempo, não pela qtd de quadros (celular lento não "perde" o movimento)
fun smoothFactor(elapsedMs: Long, smoothMs: Long = SMOOTH_MS): Float =
    if (smoothMs <= 0) 1f else 1f - exp(-elapsedMs.coerceIn(0L, 2_000L) / smoothMs.toFloat())

// esse side é o lado esquerdo?
private fun PoseFrame.isLeft(side: Side) = this[Joint.LEFT_SHOULDER] == side.shoulder || this[Joint.LEFT_HIP] == side.hip

// segura o aviso na tela um tempinho (1,5 s)
internal class StickyCue(private val durationMs: Long = 1_500) {
    private var cue: Cue? = null
    private var until = 0L
    fun set(c: Cue, now: Long) {
        cue = c
        until = now + durationMs
    }
    fun get(now: Long): Cue? = cue?.takeIf { now < until }
}

// flexão de lado: conta pelo cotovelo (~120° embaixo, ~145° em cima = 1)
// só vale de lado, deitado, perna esticada e corpo reto; ombro→mão entra de reforço
class PushupTracker : PoseTracker {
    private val sticky = StickyCue()
    private val reach = Reference()
    private var depthSmooth: Float? = null
    private var smoothAt = 0L
    private var isDown = false
    private var deepestSinceTop = 0f
    private var lastRepAt = Long.MIN_VALUE / 2
    private var badSince: Long? = null
    override var count = 0
        private set

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        fun problem(cue: Cue, debug: String?) = problem(cue, debug, timeMs)
        if (frame == null) return problem(Cue.NOT_VISIBLE, null)
        val side = frame.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)
            ?: return problem(Cue.NOT_VISIBLE, "braço não aparece")
        val left = frame.isLeft(side)
        val hip = frame[if (left) Joint.LEFT_HIP else Joint.RIGHT_HIP]
        val knee = frame[if (left) Joint.LEFT_KNEE else Joint.RIGHT_KNEE]
        val ankle = frame[if (left) Joint.LEFT_ANKLE else Joint.RIGHT_ANKLE]
        val feet = ankle ?: knee
        if (hip == null || feet == null) return problem(Cue.NOT_VISIBLE, "quadril/pernas não aparecem")

        // de frente os ombros ficam bem separados (de lado, um atrás do outro)
        val other = frame[if (left) Joint.RIGHT_SHOULDER else Joint.LEFT_SHOULDER]
        val upperArm = hypot(side.elbow.x - side.shoulder.x, side.elbow.y - side.shoulder.y)
        if (other != null && hypot(other.x - side.shoulder.x, other.y - side.shoulder.y) > FRONTAL_WIDTH * upperArm) {
            return problem(Cue.TURN_SIDEWAYS, "de frente")
        }
        // mãos embaixo dos ombros e corpo deitado, senão é gente em pé/sentada
        if (below(side.shoulder, side.wrist) <= 0f || lean(hip, side.shoulder) < STANDING_LEAN) {
            return problem(Cue.GET_READY, "fora da posição")
        }
        // Pernas esticadas: joelho no chão não vale.
        if (knee != null && ankle != null) {
            val legAngle = Geometry.angle(hip, knee, ankle)
            if (legAngle < KNEE_BENT) return problem(Cue.KNEES_UP, "joelho ${legAngle.toInt()}°")
        }
        // Corpo reto do ombro aos pés.
        val line = Geometry.angle(side.shoulder, hip, feet)
        if (line < BODY_LINE) {
            val sagging = Geometry.belowLine(hip, side.shoulder, feet) > 0
            return problem(if (sagging) Cue.HIPS_UP else Cue.HIPS_DOWN, "corpo ${line.toInt()}°")
        }

        badSince = null
        // Cotovelo: 160° (esticado) = 0, 95° = 1.
        val elbow = Geometry.angle(side.shoulder, side.elbow, side.wrist)
        val byElbow = ((160f - elbow) / 65f).coerceIn(0f, 1f)
        // reforço: ombro→mão vs braço esticado (85% ≈ cotovelo 117° = 0,6)
        val dist = hypot(side.wrist.x - side.shoulder.x, side.wrist.y - side.shoulder.y)
        val top = reach.update(dist, timeMs)
        val reachRatio = if (top > 0f) dist / top else 1f
        val byReach = ((1f - reachRatio) / REACH_FULL_DROP).coerceIn(0f, 1f)
        val raw = maxOf(byElbow, byReach)
        val depth = depthSmooth?.let { it + smoothFactor(timeMs - smoothAt) * (raw - it) } ?: raw
        depthSmooth = depth
        smoothAt = timeMs

        var counted = false
        if (!isDown && depth >= DOWN_AT) {
            isDown = true
        } else if (isDown && depth <= UP_AT) {
            isDown = false
            if (timeMs - lastRepAt >= MIN_REP_MS) {
                count++
                lastRepAt = timeMs
                counted = true
            }
            deepestSinceTop = 0f
        }
        // Desceu um pouco e voltou sem chegar ao ponto: "Desça mais!".
        if (!isDown) {
            deepestSinceTop = maxOf(deepestSinceTop, depth)
            if (depth <= UP_AT) {
                if (!counted && deepestSinceTop in 0.3f..DOWN_AT) sticky.set(Cue.GO_LOWER, timeMs)
                deepestSinceTop = 0f
            }
        }
        val cue = sticky.get(timeMs) ?: if (isDown) Cue.GO_UP else Cue.GO_DOWN
        val debug = "cotovelo ${elbow.toInt()}° · ombro→mão ${(reachRatio * 100).toInt()}% · corpo ${line.toInt()}° · descida ${(depth * 100).toInt()}%"
        return TrackerUpdate(if (counted) 1 else 0, (depth / DOWN_AT).coerceIn(0f, 1f), cue, debug)
    }

    // fora da posição: não conta e avisa o que corrigir
    // só esquece a descida depois de GRACE_MS, 1 quadro ruim solto não apaga
    private fun problem(cue: Cue, debug: String?, timeMs: Long): TrackerUpdate {
        val since = badSince ?: timeMs.also { badSince = it }
        if (timeMs - since >= GRACE_MS) {
            depthSmooth = null
            isDown = false
            deepestSinceTop = 0f
        }
        return TrackerUpdate(0, 0f, cue, debug)
    }

    // positivo = p abaixo de ref (3D quando dá)
    private fun below(ref: Point, p: Point): Float {
        val a = ref.world
        val b = p.world
        return if (a != null && b != null) b.y - a.y else p.y - ref.y
    }

    // tronco: 0 = em pé, 90 = deitado (3D quando dá)
    private fun lean(hip: Point, shoulder: Point): Float {
        val h = hip.world
        val s = shoulder.world
        return if (h != null && s != null) Geometry.leanFromVertical3d(h, s) else Geometry.leanFromVertical(hip, shoulder)
    }

    // lembra o braço esticado lá em cima; esquece 2%/s e zera se sumir por RESET_MS
    private class Reference {
        private var top = 0f
        private var at = Long.MIN_VALUE / 2

        fun update(value: Float, timeMs: Long): Float {
            val elapsed = timeMs - at
            top = if (elapsed > RESET_MS) value else maxOf(value, top * (1f - FORGET_PER_S * elapsed / 1000f))
            at = timeMs
            return top
        }
    }

    companion object {
        // embaixo: cotovelo ~120°
        const val DOWN_AT = 0.6f
        // em cima de novo: cotovelo ~145°
        const val UP_AT = 0.25f
        // mais rápido que isso é tremida
        const val MIN_REP_MS = 400L
        // tronco menos inclinado que isso = em pé ou sentado
        const val STANDING_LEAN = 45f
        // joelho mais dobrado que isso = joelho no chão (perna reta ~170°)
        const val KNEE_BENT = 145f
        // abaixo disso o quadril tá caído ou empinado
        const val BODY_LINE = 140f
        // ombros mais separados que isso (medido em braços) = de frente
        const val FRONTAL_WIDTH = 0.7f
        // ombro a 75% da distância até a mão = fundo (cotovelo ~97°)
        const val REACH_FULL_DROP = 0.25f
        const val FORGET_PER_S = 0.02f
        const val RESET_MS = 1_500L
        // tempo fora da posição até perder a flexão em andamento
        const val GRACE_MS = 400L
    }
}

class SquatTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 100f, extended = 160f, partial = 140f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        val lean = Geometry.leanFromVertical(side.hip, side.shoulder)
        // deitado ou de quatro não é agachamento
        if (lean > 70f) {
            counter.reset()
            return TrackerUpdate(0, 0f, Cue.STAND, "tronco ${lean.toInt()}°")
        }
        val knee = Geometry.angle(side.hip, side.knee, side.ankle)
        val r = counter.update(knee, timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs)
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_DOWN
            AngleRepCounter.Phase.CONTRACTED -> if (lean > 55f) Cue.CHEST_UP else Cue.GO_UP
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue, "joelho ${knee.toInt()}° · tronco ${lean.toInt()}°")
    }
}

// afundo de lado: pega o joelho mais dobrado (o da frente vai até ~90°)
class LungeTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 105f, extended = 155f, partial = 130f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        fun knee(hip: Joint, knee: Joint, ankle: Joint): Float? {
            val h = frame?.get(hip) ?: return null
            val k = frame[knee] ?: return null
            val a = frame[ankle] ?: return null
            return Geometry.angle(h, k, a)
        }
        val angle = listOfNotNull(
            knee(Joint.LEFT_HIP, Joint.LEFT_KNEE, Joint.LEFT_ANKLE),
            knee(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE),
        ).minOrNull() ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP)
        val lean = side?.let { Geometry.leanFromVertical(it.hip, it.shoulder) } ?: 0f
        // Deitado ou sentado no chão não é afundo.
        if (lean > 70f) {
            counter.reset()
            return TrackerUpdate(0, 0f, Cue.STAND, "tronco ${lean.toInt()}°")
        }
        val r = counter.update(angle, timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs)
        val leaning = lean > 35f
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_DOWN
            AngleRepCounter.Phase.CONTRACTED -> if (leaning) Cue.CHEST_UP else Cue.GO_UP
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue, "joelho ${angle.toInt()}°")
    }
}

// cadeirinha na parede: conta segundos com joelho entre 70° e 120° e tronco em pé
class WallSitTracker(private val maxGapMs: Long = 1_000) : PoseTracker {
    override var count = 0
        private set

    private var heldMs = 0L
    private var lastMs: Long? = null

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val previous = lastMs
        lastMs = timeMs
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)
            ?: return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        val knee = Geometry.angle(side.hip, side.knee, side.ankle)
        val upright = Geometry.leanFromVertical(side.hip, side.shoulder) < 45f
        val cue = when {
            !upright -> Cue.CHEST_UP
            knee > 120f -> Cue.GO_LOWER
            knee < 70f -> Cue.GO_UP
            else -> Cue.HOLD
        }
        if (cue != Cue.HOLD) return TrackerUpdate(0, 0f, cue)
        if (previous != null) heldMs += (timeMs - previous).coerceIn(0, maxGapMs)
        val seconds = (heldMs / 1000).toInt()
        val counted = seconds - count
        count = seconds
        return TrackerUpdate(counted, (heldMs % 1000) / 1000f, Cue.HOLD)
    }
}

// abdominal: ombro-quadril-joelho, deitado ~120°+, sentado ~70°
class SitupTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 75f, extended = 115f, partial = 95f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        // em pé a coxa fica vertical, dobrar o tronco não é abdominal
        val thigh = Geometry.leanFromVertical(side.hip, side.knee)
        if (thigh < 30f) {
            counter.reset()
            return TrackerUpdate(0, 0f, Cue.FLOOR, "coxa ${thigh.toInt()}°")
        }
        val hipAngle = Geometry.angle(side.shoulder, side.hip, side.knee)
        val r = counter.update(hipAngle, timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs) // aqui "mais": subir mais
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_UP
            AngleRepCounter.Phase.CONTRACTED -> Cue.GO_DOWN
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue, "quadril ${hipAngle.toInt()}°")
    }
}

// barra: cotovelo, esticado ~160°, queixo na barra ~60°
class PullupTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 70f, extended = 150f, partial = 110f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        // mãos têm que estar acima dos ombros, senão é rosca e não barra
        if (side.wrist.y >= side.shoulder.y) {
            counter.reset()
            return TrackerUpdate(0, 0f, Cue.GET_READY, "mãos abaixo dos ombros")
        }
        val elbow = Geometry.angle(side.shoulder, side.elbow, side.wrist)
        val r = counter.update(elbow, timeMs)
        if (r.partial) sticky.set(Cue.PULL, timeMs)
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.EXTEND
            AngleRepCounter.Phase.EXTENDED -> Cue.PULL
            AngleRepCounter.Phase.CONTRACTED -> Cue.EXTEND
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue, "cotovelo ${elbow.toInt()}°")
    }
}

// A (descanso) → B (ativa) → A = 1 rep; precisa de uns quadros seguidos em cada pra ignorar tremida
class StateCycleCounter(private val stableFrames: Int = 2, private val minRepMs: Long = 400) {
    enum class State { A, B }

    var count = 0
        private set
    var state: State? = null
        private set

    private var candidate: State? = null
    private var streak = 0
    private var visitedB = false
    private var lastRepAt = Long.MIN_VALUE / 2

    // true quando fechou uma rep
    fun update(observed: State?, timeMs: Long): Boolean {
        if (observed == null) return false
        streak = if (observed == candidate) streak + 1 else 1
        candidate = observed
        if (streak < stableFrames || observed == state) return false

        state = observed
        if (observed == State.B) {
            visitedB = true
            return false
        }
        if (visitedB && timeMs - lastRepAt >= minRepMs) {
            visitedB = false
            count++
            lastRepAt = timeMs
            return true
        }
        visitedB = false
        return false
    }
}

// polichinelo de frente: aberto = mãos acima dos ombros e pés afastados
class JumpingJackTracker : PoseTracker {
    private val counter = StateCycleCounter()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val ls = frame?.get(Joint.LEFT_SHOULDER)
        val rs = frame?.get(Joint.RIGHT_SHOULDER)
        val lw = frame?.get(Joint.LEFT_WRIST)
        val rw = frame?.get(Joint.RIGHT_WRIST)
        val la = frame?.get(Joint.LEFT_ANKLE)
        val ra = frame?.get(Joint.RIGHT_ANKLE)
        if (ls == null || rs == null || lw == null || rw == null || la == null || ra == null) {
            return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        }
        val shoulderWidth = kotlin.math.abs(ls.x - rs.x).coerceAtLeast(1f)
        val handsUp = lw.y < ls.y && rw.y < rs.y
        val handsDown = lw.y > ls.y && rw.y > rs.y
        val feetApart = kotlin.math.abs(la.x - ra.x) / shoulderWidth
        val observed = when {
            handsUp && feetApart > 1.2f -> StateCycleCounter.State.B
            handsDown && feetApart < 1.0f -> StateCycleCounter.State.A
            else -> null
        }
        val counted = counter.update(observed, timeMs)
        val cue = when (counter.state) {
            null -> Cue.GET_READY
            StateCycleCounter.State.A -> Cue.OPEN
            StateCycleCounter.State.B -> Cue.CLOSE
        }
        val progress = if (counter.state == StateCycleCounter.State.B) 1f else 0f
        return TrackerUpdate(if (counted) 1 else 0, progress, cue)
    }
}

// burpee de lado: em pé → chão → em pé = 1
class BurpeeTracker : PoseTracker {
    private val counter = StateCycleCounter()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP)
            ?: return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        val lean = Geometry.leanFromVertical(side.hip, side.shoulder)
        val observed = when {
            lean < 30f -> StateCycleCounter.State.A
            lean > 60f -> StateCycleCounter.State.B
            else -> null
        }
        val counted = counter.update(observed, timeMs)
        val cue = when (counter.state) {
            null -> Cue.GET_READY
            StateCycleCounter.State.A -> Cue.FLOOR
            StateCycleCounter.State.B -> Cue.STAND
        }
        return TrackerUpdate(if (counted) 1 else 0, (lean / 90f).coerceIn(0f, 1f), cue)
    }
}

// prancha: conta segundos com o corpo reto (≥ 155°), senão avisa do quadril
class PlankTracker(private val maxGapMs: Long = 1_000) : PoseTracker {
    override var count = 0
        private set

    private var heldMs = 0L
    private var lastMs: Long? = null

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val previous = lastMs
        lastMs = timeMs
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.ANKLE)
            ?: return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        val lying = Geometry.leanFromVertical(side.ankle, side.shoulder) > 60f
        val line = Geometry.angle(side.shoulder, side.hip, side.ankle)
        val knee = frame[if (frame.isLeft(side)) Joint.LEFT_KNEE else Joint.RIGHT_KNEE]
        val kneesDown = knee != null && Geometry.angle(side.hip, knee, side.ankle) < 145f
        val aligned = lying && line >= 155f && !kneesDown
        if (!aligned) {
            val cue = when {
                !lying -> Cue.GET_READY
                kneesDown -> Cue.KNEES_UP
                Geometry.belowLine(side.hip, side.shoulder, side.ankle) > 0 -> Cue.HIPS_UP
                else -> Cue.HIPS_DOWN
            }
            return TrackerUpdate(0, 0f, cue)
        }
        if (previous != null) heldMs += (timeMs - previous).coerceIn(0, maxGapMs)
        val seconds = (heldMs / 1000).toInt()
        val counted = seconds - count
        count = seconds
        return TrackerUpdate(counted, (heldMs % 1000) / 1000f, Cue.HOLD)
    }
}
