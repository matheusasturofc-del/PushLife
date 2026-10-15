package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import com.pushlife.app.core.pose.classify.EmaSmoothing
import com.pushlife.app.core.pose.classify.PoseClassifier
import com.pushlife.app.core.pose.classify.RepetitionCounter

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
 * Flexão com o classificador de poses do Google ML Kit: compara cada quadro com centenas de fotos
 * reais de flexão "em cima" e "embaixo" e conta quando a pessoa entra na pose de baixo e sai dela.
 * Como olha o corpo inteiro, mexer só a cabeça não muda nada; as poses de referência também
 * incluem gente em pé (agachamento), então ficar em pé não conta. Funciona de lado ou de frente.
 */
class PushupTracker(private val classifier: () -> PoseClassifier?) : PoseTracker {
    // O modelo preciso entrega menos quadros por segundo: a suavização tolera intervalos maiores.
    private val smoothing = EmaSmoothing(resetMs = 1_500)
    // Medido com as fotos do Google: confiança 1,8 ≈ descer uns 50–55% do caminho (cotovelo perto de 90°,
    // longe do chão); abaixo de 1 ≈ já subiu de volta.
    private val reps = RepetitionCounter(DOWN, enter = ENTER, exit = 1f)
    private val sticky = StickyCue()
    private var deepestSinceTop = 0f
    override val count get() = reps.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val c = classifier() ?: return TrackerUpdate(0, 0f, Cue.GET_READY) // ainda carregando
        if (frame == null || frame.all.isEmpty()) {
            smoothing.smooth(emptyMap(), timeMs)
            return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        }
        val result = smoothing.smooth(c.classify(frame.all), timeMs)
        val counted = reps.add(result)
        val down = result[DOWN] ?: 0f
        val up = result[UP] ?: 0f
        // Desceu um pouco e voltou sem chegar ao ponto: aí sim "Desça mais!".
        deepestSinceTop = maxOf(deepestSinceTop, down)
        if (!reps.inPose && down < 0.3f) {
            if (!counted && deepestSinceTop in 0.6f..ENTER) sticky.set(Cue.GO_LOWER, timeMs)
            deepestSinceTop = 0f
        }
        if (counted) deepestSinceTop = 0f
        val cue = sticky.get(timeMs) ?: when {
            reps.inPose -> Cue.GO_UP
            up >= 4f || down > 0f -> Cue.GO_DOWN
            else -> Cue.GET_READY
        }
        return TrackerUpdate(if (counted) 1 else 0, (down / ENTER).coerceIn(0f, 1f), cue)
    }

    companion object {
        const val ENTER = 1.8f
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
