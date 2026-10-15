package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import com.pushlife.app.core.pose.classify.EmaSmoothing
import com.pushlife.app.core.pose.classify.PoseClassifier
import kotlin.math.hypot

/** Dica mostrada durante o treino (o texto de cada uma fica nos recursos de idioma). */
enum class Cue {
    NOT_VISIBLE, GET_READY,
    GO_DOWN, GO_UP, GO_LOWER,
    CHEST_UP, HIPS_UP, HIPS_DOWN, HOLD,
    OPEN, CLOSE, FLOOR, STAND, PULL, EXTEND,
}

/**
 * @param counted unidades concluídas neste quadro (repetições, ou segundos na prancha)
 * @param progress 0..1 para a barra de profundidade
 */
data class TrackerUpdate(val counted: Int, val progress: Float, val cue: Cue)

/** Acompanha um exercício quadro a quadro a partir da pose. */
interface PoseTracker {
    val count: Int
    fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate

    companion object {
        /** [classifier] só é usado na flexão (poses de referência carregadas do app). */
        fun forExercise(exercise: Exercise, classifier: () -> PoseClassifier? = { null }): PoseTracker = when (exercise) {
            Exercise.PUSHUP -> PushupTracker(classifier)
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
    private val smoothing: Float = 0.5f,
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
        val a = angle?.let { it + smoothing * (raw - it) } ?: raw
        angle = a
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
 * Flexão medida de dois jeitos ao mesmo tempo; vale o que perceber mais descida:
 * - o classificador do Google ML Kit (compara o corpo inteiro com centenas de fotos reais de flexão);
 * - o ângulo do cotovelo no esqueleto (bom com a câmera de lado);
 * - o quanto o ombro chegou perto da mão, comparado com o braço esticado lá em cima (bom com a câmera
 *   de frente, onde o cotovelo dobra "para dentro" da tela e o ângulo quase não muda na imagem).
 * Desce até a "profundidade" [DOWN_AT] e volta até [UP_AT] = 1 flexão.
 * Mexer só a cabeça não muda nenhum dos dois; em pé dobrando o braço não vale (o cotovelo só conta
 * com as mãos abaixo dos ombros e o tronco fora da vertical, e o classificador separa em pé de flexão).
 */
class PushupTracker(private val classifier: () -> PoseClassifier?) : PoseTracker {
    // O modelo mais preciso entrega menos quadros por segundo: a suavização tolera intervalos maiores.
    private val smoothing = EmaSmoothing(resetMs = 1_500)
    private val sticky = StickyCue()
    private var angleDepth: Float? = null
    private var isDown = false
    private var deepestSinceTop = 0f
    private var lastRepAt = Long.MIN_VALUE / 2
    private var maxReach = 0f
    private var reachAt = Long.MIN_VALUE / 2
    override var count = 0
        private set

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        if (frame == null) {
            smoothing.smooth(emptyMap(), timeMs)
            return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        }
        // 1) Classificador: confiança 3 (de 10) já é "embaixo" de verdade (medido com as fotos do Google).
        val c = classifier()
        val classes = if (c != null && frame.all.isNotEmpty()) smoothing.smooth(c.classify(frame.all), timeMs) else emptyMap()
        val classifierDepth = ((classes[DOWN] ?: 0f) / 3f).coerceIn(0f, 1f)
        // 2) Braço: cotovelo (160° esticado = 0, 95° = 1) ou ombro perto da mão. Suavizado para não tremer.
        val raw = armDepth(frame, timeMs)
        angleDepth = raw?.let { r -> angleDepth?.let { it + 0.5f * (r - it) } ?: r }
        if (raw == null) angleDepth = null
        if (classes.isEmpty() && angleDepth == null) {
            // Corpo na câmera, mas as poses de referência ainda estão carregando: só espera.
            return TrackerUpdate(0, 0f, if (c == null && frame.all.isNotEmpty()) Cue.GET_READY else Cue.NOT_VISIBLE)
        }

        val depth = maxOf(classifierDepth, angleDepth ?: 0f)
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
        // Desceu um pouco e voltou sem chegar ao ponto: aí sim "Desça mais!".
        if (!isDown) {
            deepestSinceTop = maxOf(deepestSinceTop, depth)
            if (depth <= UP_AT) {
                if (!counted && deepestSinceTop in 0.3f..DOWN_AT) sticky.set(Cue.GO_LOWER, timeMs)
                deepestSinceTop = 0f
            }
        }
        val cue = sticky.get(timeMs) ?: if (isDown) Cue.GO_UP else Cue.GO_DOWN
        return TrackerUpdate(if (counted) 1 else 0, (depth / DOWN_AT).coerceIn(0f, 1f), cue)
    }

    /** Quanto o braço dobrou (0..1), ou null se o braço não aparece ou a pessoa está em pé. */
    private fun armDepth(frame: PoseFrame, timeMs: Long): Float? {
        val side = frame.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST) ?: return null
        val hip = frame[if (frame[Joint.LEFT_SHOULDER] == side.shoulder) Joint.LEFT_HIP else Joint.RIGHT_HIP]
        val shoulder3d = side.shoulder.world
        val wrist3d = side.wrist.world
        if (shoulder3d != null && wrist3d != null) {
            // Em 3D dá para olhar o corpo de verdade, não a imagem: vale com a câmera de frente ou de lado.
            if (wrist3d.y <= shoulder3d.y) return null // mãos acima dos ombros: não é flexão
            val hip3d = hip?.world
            if (hip3d != null && Geometry.leanFromVertical3d(hip3d, shoulder3d) < STANDING_LEAN) return null // tronco em pé
        } else if (side.wrist.y <= side.shoulder.y) {
            return null // mãos acima dos ombros: não é flexão
        } else if (hip != null) {
            // Em pé: tronco na vertical e quadril bem abaixo do ombro (mais que o comprimento do braço).
            val upperArm = kotlin.math.hypot(side.elbow.x - side.shoulder.x, side.elbow.y - side.shoulder.y)
            val standing = Geometry.leanFromVertical(hip, side.shoulder) < 35f && hip.y - side.shoulder.y > 1.1f * upperArm
            if (standing) return null
        }
        val angle = Geometry.angle(side.shoulder, side.elbow, side.wrist)
        val byAngle = ((160f - angle) / 65f).coerceIn(0f, 1f)
        return maxOf(byAngle, reachDepth(side, timeMs))
    }

    /**
     * Distância ombro→mão em relação à maior vista há pouco (o braço esticado em cima). Vale de qualquer
     * lado: ao descer, o ombro chega perto da mão. 85% da distância (~cotovelo a 117°) = profundidade 0,6.
     * A referência esquece devagar (2% por segundo) para se ajustar se a pessoa se afastar da câmera.
     */
    private fun reachDepth(side: Side, timeMs: Long): Float {
        val reach = hypot(side.wrist.x - side.shoulder.x, side.wrist.y - side.shoulder.y)
        val elapsed = timeMs - reachAt
        maxReach = if (elapsed > REACH_RESET_MS || maxReach <= 0f) {
            reach
        } else {
            maxOf(reach, maxReach * (1f - REACH_FORGET_PER_S * elapsed / 1000f))
        }
        reachAt = timeMs
        if (maxReach <= 0f) return 0f
        return ((1f - reach / maxReach) / REACH_FULL_DROP).coerceIn(0f, 1f)
    }

    companion object {
        /** Profundidade para valer como "embaixo": cotovelo a ~120° ou confiança ~1,8 no classificador. */
        const val DOWN_AT = 0.6f
        /** Duas flexões mais rápido que isso é tremida, não flexão. */
        const val MIN_REP_MS = 400L
        /** De volta em cima: cotovelo a ~145° e classificador quase sem "embaixo". */
        const val UP_AT = 0.25f
        /** Tronco a menos que isso da vertical (em 3D) é gente em pé ou sentada, não flexão. */
        const val STANDING_LEAN = 45f
        /** Ombro a 75% da distância de cima até a mão = profundidade 1 (cotovelo ~97°). */
        const val REACH_FULL_DROP = 0.25f
        const val REACH_FORGET_PER_S = 0.02f
        /** Sem ver o braço por esse tempo, mede de novo o braço esticado. */
        const val REACH_RESET_MS = 1_500L
        const val DOWN = "pushups_down"
        const val UP = "pushups_up"
    }
}

class SquatTracker : PoseTracker {
    private val counter = AngleRepCounter(contracted = 100f, extended = 160f, partial = 140f)
    private val sticky = StickyCue()
    override val count get() = counter.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)
            ?: return TrackerUpdate(0, counter.progress, Cue.NOT_VISIBLE)
        val r = counter.update(Geometry.angle(side.hip, side.knee, side.ankle), timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs)
        val lean = Geometry.leanFromVertical(side.hip, side.shoulder)
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_DOWN
            AngleRepCounter.Phase.CONTRACTED -> if (lean > 55f) Cue.CHEST_UP else Cue.GO_UP
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue)
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
        val r = counter.update(angle, timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs)
        val side = frame?.bestSide(SidePart.SHOULDER, SidePart.HIP)
        val leaning = side != null && Geometry.leanFromVertical(side.hip, side.shoulder) > 35f
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_DOWN
            AngleRepCounter.Phase.CONTRACTED -> if (leaning) Cue.CHEST_UP else Cue.GO_UP
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue)
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
        val r = counter.update(Geometry.angle(side.shoulder, side.hip, side.knee), timeMs)
        if (r.partial) sticky.set(Cue.GO_LOWER, timeMs) // aqui "mais": subir mais
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> Cue.GO_UP
            AngleRepCounter.Phase.CONTRACTED -> Cue.GO_DOWN
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue)
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
        val r = counter.update(Geometry.angle(side.shoulder, side.elbow, side.wrist), timeMs)
        if (r.partial) sticky.set(Cue.PULL, timeMs)
        val cue = sticky.get(timeMs) ?: when (counter.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.EXTEND
            AngleRepCounter.Phase.EXTENDED -> Cue.PULL
            AngleRepCounter.Phase.CONTRACTED -> Cue.EXTEND
        }
        return TrackerUpdate(if (r.counted) 1 else 0, counter.progress, cue)
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
        val aligned = lying && line >= 155f
        if (!aligned) {
            val cue = when {
                !lying -> Cue.GET_READY
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
