package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import kotlin.math.exp
import kotlin.math.hypot

/** Dica mostrada durante o treino (o texto de cada uma fica nos recursos de idioma). */
enum class Cue {
    NOT_VISIBLE, GET_READY,
    GO_DOWN, GO_UP, GO_LOWER,
    CHEST_UP, HIPS_UP, HIPS_DOWN, HOLD, KNEES_UP, TURN_SIDEWAYS,
    OPEN, CLOSE, FLOOR, STAND, PULL, EXTEND,
}

/**
 * @param counted unidades concluídas neste quadro (repetições, ou segundos na prancha)
 * @param progress 0..1 para a barra de profundidade
 * @param debug o que o contador está vendo, em texto curto, para mostrar pequeno na tela
 */
data class TrackerUpdate(val counted: Int, val progress: Float, val cue: Cue, val debug: String? = null)

/** Acompanha um exercício quadro a quadro a partir da pose. */
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

/**
 * Conta repetições por um ângulo que diminui no fundo do movimento (joelho no agachamento,
 * quadril no abdominal, cotovelo na barra). Histerese: precisa passar de [contracted] e
 * voltar a [extended]. Se voltar sem chegar ao fundo, mas passando de [partial], é meia
 * repetição: não conta e gera o aviso "Desça mais!".
 */
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

    /** Esquece a repetição em andamento (ex.: a pessoa saiu da posição). */
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

/** Constante de tempo da suavização dos ângulos. */
const val SMOOTH_MS = 70L

/**
 * Quanto um quadro novo pesa na média, pelo tempo desde o anterior (não pelo número de quadros):
 * em celular lento, com poucos quadros por segundo, cada um pesa mais e o movimento não "some".
 */
fun smoothFactor(elapsedMs: Long, smoothMs: Long = SMOOTH_MS): Float =
    if (smoothMs <= 0) 1f else 1f - exp(-elapsedMs.coerceIn(0L, 2_000L) / smoothMs.toFloat())

/** O lado do corpo (esquerdo/direito) que [side] usa: as articulações daquele lado. */
private fun PoseFrame.isLeft(side: Side) = this[Joint.LEFT_SHOULDER] == side.shoulder || this[Joint.LEFT_HIP] == side.hip

/** Mantém um aviso na tela por um tempo (ex.: "Desça mais!" por 1,5 s). */
private class StickyCue(private val durationMs: Long = 1_500) {
    private var cue: Cue? = null
    private var until = 0L
    fun set(c: Cue, now: Long) {
        cue = c
        until = now + durationMs
    }
    fun get(now: Long): Cue? = cue?.takeIf { now < until }
}

/**
 * Flexão, com o celular de lado: conta pelo **cotovelo** (em 3D quando o detector dá a profundidade),
 * e só com o corpo em posição de flexão:
 * - de lado para a câmera (de frente o cotovelo dobra "para dentro" da tela e não dá para medir);
 * - corpo deitado (tronco longe da vertical) e as mãos abaixo dos ombros;
 * - pernas esticadas (joelho no chão não vale) e corpo reto (quadril nem caído nem empinado).
 * Desce até a "profundidade" [DOWN_AT] (cotovelo ~120°) e volta até [UP_AT] (~145°) = 1 flexão.
 * Como reforço, mede também quanto o ombro chegou perto da mão em relação ao braço esticado.
 */
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

        // De frente: os dois ombros aparecem bem separados na imagem (de lado ficam um atrás do outro).
        val other = frame[if (left) Joint.RIGHT_SHOULDER else Joint.LEFT_SHOULDER]
        val upperArm = hypot(side.elbow.x - side.shoulder.x, side.elbow.y - side.shoulder.y)
        if (other != null && hypot(other.x - side.shoulder.x, other.y - side.shoulder.y) > FRONTAL_WIDTH * upperArm) {
            return problem(Cue.TURN_SIDEWAYS, "de frente")
        }
        // Posição: mãos abaixo dos ombros e corpo deitado (senão é gente em pé, sentada...).
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
        // Ombro→mão em relação ao braço esticado lá em cima: 85% (~cotovelo a 117°) = 0,6.
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

    /**
     * Fora da posição (ou sem enxergar o corpo): não conta e avisa o que corrigir. Se durar mais que
     * [GRACE_MS], esquece a descida em andamento; um quadro ruim solto (a IA perdeu um ponto) não apaga.
     */
    private fun problem(cue: Cue, debug: String?, timeMs: Long): TrackerUpdate {
        val since = badSince ?: timeMs.also { badSince = it }
        if (timeMs - since >= GRACE_MS) {
            depthSmooth = null
            isDown = false
            deepestSinceTop = 0f
        }
        return TrackerUpdate(0, 0f, cue, debug)
    }

    /** Quanto [p] está abaixo de [ref] (positivo = abaixo). Em 3D quando dá. */
    private fun below(ref: Point, p: Point): Float {
        val a = ref.world
        val b = p.world
        return if (a != null && b != null) b.y - a.y else p.y - ref.y
    }

    /** Inclinação do tronco em relação à vertical (0 = em pé, 90 = deitado). Em 3D quando dá. */
    private fun lean(hip: Point, shoulder: Point): Float {
        val h = hip.world
        val s = shoulder.world
        return if (h != null && s != null) Geometry.leanFromVertical3d(h, s) else Geometry.leanFromVertical(hip, shoulder)
    }

    /**
     * O maior valor visto há pouco (o braço esticado lá em cima). Esquece devagar (2% por segundo) para
     * se ajustar se a pessoa mudar de lugar; sem ver nada por [RESET_MS], começa de novo.
     */
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
        /** Profundidade para valer como "embaixo": cotovelo a ~120°. */
        const val DOWN_AT = 0.6f
        /** De volta em cima: cotovelo a ~145°. */
        const val UP_AT = 0.25f
        /** Duas flexões mais rápido que isso é tremida, não flexão. */
        const val MIN_REP_MS = 400L
        /** Tronco a menos que isso da vertical é gente em pé ou sentada, não flexão. */
        const val STANDING_LEAN = 45f
        /** Joelho mais dobrado que isso = joelho no chão (na flexão a perna fica reta, ~170°). */
        const val KNEE_BENT = 145f
        /** Ombro-quadril-pés mais dobrado que isso = quadril caído ou empinado demais. */
        const val BODY_LINE = 140f
        /** Ombros separados mais que isso (em braços) = de frente para a câmera. */
        const val FRONTAL_WIDTH = 0.7f
        /** Ombro a 75% da distância de cima até a mão = profundidade 1 (cotovelo ~97°). */
        const val REACH_FULL_DROP = 0.25f
        const val FORGET_PER_S = 0.02f
        const val RESET_MS = 1_500L
        /** Fora da posição por mais que isso: a flexão em andamento não vale mais. */
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
        // Deitado ou de quatro não é agachamento (o joelho dobra, mas o tronco está deitado).
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

/**
 * Afundo, de lado: conta pelo joelho mais dobrado (o da frente desce até uns 90°).
 * Usa as duas pernas quando aparecem; se só uma aparece, usa ela.
 */
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

/**
 * Agachamento isométrico (cadeira na parede): conta segundos parado com o joelho entre 70° e 120°
 * e o tronco de pé. Fora disso diz se precisa descer ou subir um pouco.
 */
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

/** Abdominal: ângulo ombro-quadril-joelho. Deitado ≈ 120°+, sentado ≈ 70°. */
class SitupTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 75f, extended = 115f, partial = 95f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        // Em pé a coxa fica na vertical: dobrar o tronco para a frente não é abdominal.
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

/** Barra fixa: ângulo do cotovelo. Braços esticados ≈ 160°, queixo na barra ≈ 60°. */
class PullupTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 70f, extended = 150f, partial = 110f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        // Pendurado: mãos acima dos ombros. Dobrar o braço com as mãos embaixo é rosca, não barra.
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

/**
 * Conta ciclos entre duas posições (A = descanso, B = ativa): A → B → A = 1 repetição.
 * Cada posição precisa aparecer em [stableFrames] quadros seguidos, para ignorar tremidas.
 */
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

    /** @return true quando completou uma repetição. */
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

/** Polichinelo, de frente: aberto = mãos acima dos ombros e pés afastados. */
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

/** Burpee, de lado: em pé (tronco vertical) → no chão (tronco deitado) → em pé = 1. */
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

/**
 * Prancha: conta segundos com o corpo deitado e reto (ombro-quadril-tornozelo ≥ 155°).
 * Fora do alinhamento, diz se o quadril está caído ou alto demais.
 */
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
