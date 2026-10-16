package com.pushlife.app.core.pose

import com.pushlife.app.core.Exercise
import com.pushlife.app.core.pose.classify.EmaSmoothing
import com.pushlife.app.core.pose.classify.PoseClassifier
import kotlin.math.hypot

/** Dica mostrada durante o treino (o texto de cada uma fica nos recursos de idioma). */
enum class Cue {
    NOT_VISIBLE, GET_READY,
    GO_DOWN, GO_UP, GO_LOWER,
    CHEST_UP, HIPS_UP, HIPS_DOWN, HOLD, KNEES_UP,
    OPEN, CLOSE, FLOOR, STAND, PULL, EXTEND,
}

/**
 * @param counted unidades concluídas neste quadro (repetições, ou segundos na prancha)
 * @param progress 0..1 para a barra de profundidade
 */
/** [debug]: o que o contador está vendo, em texto curto, para mostrar pequeno na tela. */
data class TrackerUpdate(val counted: Int, val progress: Float, val cue: Cue, val debug: String? = null)

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
 * Flexão medida pelo braço, de três jeitos; vale o que perceber mais descida:
 * - o ângulo do cotovelo (em 3D quando o detector dá a profundidade);
 * - o quanto o ombro chegou perto da mão, comparado com o braço esticado lá em cima;
 * - de frente para a câmera, o quanto os ombros desceram (serve mesmo com as mãos fora da imagem).
 * O classificador do Google (fotos reais de flexão) só entra quando o braço não aparece.
 * Desce até a "profundidade" [DOWN_AT] e volta até [UP_AT] = 1 flexão.
 * Não vale: mexer só a cabeça, em pé dobrando o braço, nem com os joelhos no chão (pernas dobradas).
 */
class PushupTracker(private val classifier: () -> PoseClassifier?) : PoseTracker {
    private val smoothing = EmaSmoothing(resetMs = 1_500)
    private val sticky = StickyCue()
    private val reach = Reference { it * FORGET_PER_S }
    private val shoulderTop = Reference { SHOULDER_FORGET_PER_S }
    private var depthSmooth: Float? = null
    private var smoothAt = 0L
    private var lastAngle: Float? = null
    private var lastReach: Float? = null
    private var lastDrop: Float? = null
    private var isDown = false
    private var deepestSinceTop = 0f
    private var lastRepAt = Long.MIN_VALUE / 2
    override var count = 0
        private set

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        if (frame == null) {
            smoothing.smooth(emptyMap(), timeMs)
            return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        }
        lastAngle = null
        lastReach = null
        lastDrop = null
        if (kneeling(frame)) {
            // Joelhos no chão: não conta, e avisa.
            depthSmooth = null
            return TrackerUpdate(0, 0f, Cue.KNEES_UP, "joelho no chão")
        }
        val c = classifier()
        val classes = if (c != null && frame.all.isNotEmpty()) smoothing.smooth(c.classify(frame.all), timeMs) else emptyMap()
        // Braço (cotovelo, ombro→mão, ombros descendo), suavizado pelo tempo e não por quadro: em celular
        // lento cada quadro pesa mais, senão a descida "some" antes de chegar ao ponto.
        val arm = armDepth(frame, timeMs)?.let { raw ->
            val k = 1f - kotlin.math.exp(-(timeMs - smoothAt).coerceIn(0L, 2_000L) / SMOOTH_MS.toFloat())
            (depthSmooth?.let { it + k * (raw - it) } ?: raw).also { smoothAt = timeMs }
        }
        depthSmooth = arm
        // Sem braço visível, o classificador (já suavizado): confiança 3 de 10 = embaixo.
        val depth = arm ?: classes.takeIf { it.isNotEmpty() }?.let { ((it[DOWN] ?: 0f) / 3f).coerceIn(0f, 1f) }
            // Corpo na câmera, mas as poses de referência ainda estão carregando: só espera.
            ?: return TrackerUpdate(0, 0f, if (c == null && frame.all.isNotEmpty()) Cue.GET_READY else Cue.NOT_VISIBLE)

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
        val debug = buildString {
            lastAngle?.let { append("cotovelo ${it.toInt()}° · ") }
            lastReach?.let { append("ombro→mão ${(it * 100).toInt()}% · ") }
            lastDrop?.let { append("ombros desceram ${(it * 100).toInt()}% · ") }
            if (classes.isNotEmpty()) append("IA ${"%.1f".format(classes[DOWN] ?: 0f)} · ")
            append("descida ${(depth * 100).toInt()}%")
        }
        return TrackerUpdate(if (counted) 1 else 0, (depth / DOWN_AT).coerceIn(0f, 1f), cue, debug)
    }

    /** De lado, com quadril, joelho e tornozelo visíveis: perna dobrada = joelho no chão. */
    private fun kneeling(frame: PoseFrame): Boolean {
        val sides = listOf(
            Triple(Joint.LEFT_HIP, Joint.LEFT_KNEE, Joint.LEFT_ANKLE),
            Triple(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE),
        ).mapNotNull { (h, k, a) ->
            val hip = frame[h] ?: return@mapNotNull null
            val knee = frame[k] ?: return@mapNotNull null
            val ankle = frame[a] ?: return@mapNotNull null
            Geometry.angle(hip, knee, ankle)
        }
        // As duas pernas dobradas (ou a única que aparece): de joelhos. Em pé ou de prancha, perna reta.
        return sides.isNotEmpty() && sides.all { it < KNEE_BENT } && !standingUp(frame)
    }

    /** Tronco em pé (aí a perna dobrada é gente sentada/agachada, não flexão de joelhos; o braço já não conta). */
    private fun standingUp(frame: PoseFrame): Boolean {
        val shoulder = frame[Joint.LEFT_SHOULDER] ?: frame[Joint.RIGHT_SHOULDER] ?: return false
        val hip = frame[Joint.LEFT_HIP] ?: frame[Joint.RIGHT_HIP] ?: return false
        val s3 = shoulder.world
        val h3 = hip.world
        return if (s3 != null && h3 != null) Geometry.leanFromVertical3d(h3, s3) < STANDING_LEAN else Geometry.leanFromVertical(hip, shoulder) < 35f
    }

    /** Quanto o braço dobrou (0..1), ou null se o braço não aparece ou a pessoa está em pé. */
    private fun armDepth(frame: PoseFrame, timeMs: Long): Float? {
        val bySide = frame.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)?.let { sideDepth(frame, it, timeMs) }
        val byShoulders = shoulderDrop(frame, timeMs)
        return listOfNotNull(bySide, byShoulders).maxOrNull()
    }

    private fun sideDepth(frame: PoseFrame, side: Side, timeMs: Long): Float? {
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
        } else if (hip != null && standing2d(hip, side.shoulder, side.elbow)) {
            return null
        }
        val angle = Geometry.angle(side.shoulder, side.elbow, side.wrist)
        lastAngle = angle
        val byAngle = ((160f - angle) / 65f).coerceIn(0f, 1f)
        // Ombro→mão em relação ao braço esticado lá em cima: 85% (~cotovelo a 117°) = profundidade 0,6.
        val dist = hypot(side.wrist.x - side.shoulder.x, side.wrist.y - side.shoulder.y)
        val top = reach.update(dist, timeMs)
        if (top <= 0f) return byAngle
        lastReach = dist / top
        return maxOf(byAngle, ((1f - dist / top) / REACH_FULL_DROP).coerceIn(0f, 1f))
    }

    /** Em pé na imagem: tronco na vertical e quadril bem abaixo do ombro (mais que o comprimento do braço). */
    private fun standing2d(hip: Point, shoulder: Point, elbow: Point): Boolean {
        val upperArm = hypot(elbow.x - shoulder.x, elbow.y - shoulder.y)
        return Geometry.leanFromVertical(hip, shoulder) < 35f && hip.y - shoulder.y > 1.1f * upperArm
    }

    /**
     * De frente para a câmera: os dois ombros aparecem bem separados. Mede quanto eles desceram desde
     * lá em cima, em relação à largura dos ombros (que não muda na flexão). Funciona mesmo com as mãos
     * fora da imagem (celular no chão, perto das mãos).
     */
    private fun shoulderDrop(frame: PoseFrame, timeMs: Long): Float? {
        val l = frame[Joint.LEFT_SHOULDER] ?: return null
        val r = frame[Joint.RIGHT_SHOULDER] ?: return null
        val elbow = frame[Joint.LEFT_ELBOW] ?: frame[Joint.RIGHT_ELBOW] ?: return null
        val width = hypot(l.x - r.x, l.y - r.y)
        val mid = Point((l.x + r.x) / 2, (l.y + r.y) / 2)
        val upperArm = hypot(elbow.x - mid.x, elbow.y - mid.y) - width / 2
        if (width < FRONTAL_WIDTH * upperArm.coerceAtLeast(1f)) return null // de lado: ombros um atrás do outro
        if (elbow.y <= mid.y) return null // braços para cima: não é flexão
        val hip = frame[Joint.LEFT_HIP] ?: frame[Joint.RIGHT_HIP]
        if (hip != null && standing2d(hip, mid, elbow)) return null
        // Topo = o ombro mais alto visto há pouco (y menor), em larguras de ombro.
        val y = mid.y / width
        val top = -shoulderTop.update(-y, timeMs)
        val drop = (y - top).coerceAtLeast(0f)
        lastDrop = drop
        return (drop / SHOULDER_FULL_DROP).coerceIn(0f, 1f)
    }

    /**
     * O maior valor visto há pouco ("lá em cima"). Esquece devagar ([forgetPerS] por segundo) para se
     * ajustar se a pessoa mudar de lugar; sem ver nada por [RESET_MS], começa de novo.
     */
    private class Reference(private val forgetPerS: (top: Float) -> Float) {
        private var top = 0f
        private var at = Long.MIN_VALUE / 2

        fun update(value: Float, timeMs: Long): Float {
            val elapsed = timeMs - at
            top = if (elapsed > RESET_MS) value else maxOf(value, top - forgetPerS(top) * elapsed / 1000f)
            at = timeMs
            return top
        }
    }

    companion object {
        /** Profundidade para valer como "embaixo": cotovelo a ~120°, ombro a 85% da mão ou ombros 1/3 de largura abaixo. */
        const val DOWN_AT = 0.6f
        /** Duas flexões mais rápido que isso é tremida, não flexão. */
        const val MIN_REP_MS = 400L
        /** De volta em cima: cotovelo a ~145°. */
        const val UP_AT = 0.25f
        /** Tronco a menos que isso da vertical (em 3D) é gente em pé ou sentada, não flexão. */
        const val STANDING_LEAN = 45f
        /** Ombro a 75% da distância de cima até a mão = profundidade 1 (cotovelo ~97°). */
        const val REACH_FULL_DROP = 0.25f
        /** Ombros descendo 55% da largura deles = profundidade 1 (uns 20 cm). */
        const val SHOULDER_FULL_DROP = 0.55f
        /** Ombros pelo menos 0,7 braço de largura na imagem = de frente (de lado ficam quase juntos). */
        const val FRONTAL_WIDTH = 0.7f
        /** Joelho mais dobrado que isso = joelho no chão (de prancha a perna fica reta, ~170°). */
        const val KNEE_BENT = 145f
        const val FORGET_PER_S = 0.02f
        /** Os ombros "lá em cima" esquecem 3% da largura deles por segundo. */
        const val SHOULDER_FORGET_PER_S = 0.03f
        const val RESET_MS = 1_500L
        /** Constante de tempo da suavização. */
        const val SMOOTH_MS = 120L
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
