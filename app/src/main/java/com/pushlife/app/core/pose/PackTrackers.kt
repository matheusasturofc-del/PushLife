package com.pushlife.app.core.pose

import com.pushlife.app.core.packs.Move

// qual articulação mede a repetição
enum class Bend(val parts: Array<SidePart>) {
    ELBOW(arrayOf(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST)), // ombro-cotovelo-punho
    KNEE(arrayOf(SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)), // quadril-joelho-tornozelo
    HIP(arrayOf(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE)), // tronco x coxa
}

fun Side.bend(b: Bend): Float = when (b) {
    Bend.ELBOW -> Geometry.angle(shoulder, elbow, wrist)
    Bend.KNEE -> Geometry.angle(hip, knee, ankle)
    Bend.HIP -> Geometry.angle(shoulder, hip, knee)
}

// tronco: 0 = em pé, 90 = deitado
private fun Side.torsoLean() = Geometry.leanFromVertical(hip, shoulder)

// repetição por uma articulação que dobra e volta (cotovelo na flexão, joelho no agachamento...)
// rising = a rep é abrir o ângulo e voltar (ponte de glúteo); bothSides = cada perna conta (marcha)
// position: aviso quando a pessoa não está na posição do exercício (aí não conta)
class BendRepTracker(
    private val bend: Bend,
    private val contracted: Float,
    private val extended: Float,
    private val partial: Float,
    private val rising: Boolean = false,
    private val bothSides: Boolean = false,
    private val position: (Side) -> Cue? = { null },
) : PoseTracker {
    // abrir o ângulo vira "fechar" invertendo (360 - ângulo), assim o mesmo contador serve
    private fun counter() =
        if (rising) AngleRepCounter(360f - contracted, 360f - extended, 360f - partial) else AngleRepCounter(contracted, extended, partial)

    private val left = counter()
    private val right = counter()
    private val sticky = StickyCue()
    override val count get() = left.count + right.count

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        if (frame == null) return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        // um lado só: o mais visível alimenta um contador (de perfil é o que importa)
        val sides = if (bothSides) {
            listOfNotNull(frame.side(true, *bend.parts)?.let { left to it }, frame.side(false, *bend.parts)?.let { right to it })
        } else {
            listOfNotNull(frame.bestSide(*bend.parts)?.let { left to it })
        }
        if (sides.isEmpty()) return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        position(sides.first().second)?.let { cue ->
            left.reset()
            right.reset()
            return TrackerUpdate(0, 0f, cue)
        }
        var counted = 0
        var debug = ""
        for ((c, side) in sides) {
            val a = side.bend(bend)
            val r = c.update(if (rising) 360f - a else a, timeMs)
            if (r.counted) counted++
            if (r.partial) sticky.set(if (rising) Cue.GO_UP else Cue.GO_LOWER, timeMs)
            debug += "${bend.name.lowercase()} ${a.toInt()}° "
        }
        val main = sides.first().first
        val cue = sticky.get(timeMs) ?: when (main.phase) {
            AngleRepCounter.Phase.WAITING -> Cue.GET_READY
            AngleRepCounter.Phase.EXTENDED -> if (rising) Cue.GO_UP else Cue.GO_DOWN
            AngleRepCounter.Phase.CONTRACTED -> if (rising) Cue.GO_DOWN else Cue.GO_UP
        }
        return TrackerUpdate(counted, sides.maxOf { it.first.progress }, cue, debug.trim())
    }
}

// segurar uma pose (alongamento, prancha lateral...): conta os segundos em que a pose confere
// check devolve Cue.HOLD quando está certo, ou a dica do que corrigir
class PoseHoldTracker(private val maxGapMs: Long = 1_000, private val check: (PoseFrame) -> Cue) : PoseTracker {
    override var count = 0
        private set

    private var heldMs = 0L
    private var lastMs: Long? = null

    override fun update(frame: PoseFrame?, timeMs: Long): TrackerUpdate {
        val previous = lastMs
        lastMs = timeMs
        if (frame == null) return TrackerUpdate(0, 0f, Cue.NOT_VISIBLE)
        val cue = check(frame)
        if (cue != Cue.HOLD) return TrackerUpdate(0, 0f, cue)
        if (previous != null) heldMs += (timeMs - previous).coerceIn(0, maxGapMs)
        val seconds = (heldMs / 1000).toInt()
        val counted = seconds - count
        count = seconds
        return TrackerUpdate(counted, (heldMs % 1000) / 1000f, Cue.HOLD)
    }
}

// detector de cada movimento dos packs; null = a câmera não consegue conferir esse
object PackTrackers {
    private val standing: (Side) -> Cue? = { s -> if (s.torsoLean() > 45f) Cue.STAND else null }
    private val lying: (Side) -> Cue? = { s -> if (s.torsoLean() < 35f) Cue.FLOOR else null }

    private fun body(frame: PoseFrame) = frame.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.KNEE, SidePart.ANKLE)

    // as duas mãos acima da cabeça
    private fun handsUp(frame: PoseFrame, both: Boolean): Boolean {
        val nose = frame[Joint.NOSE] ?: frame[Joint.LEFT_SHOULDER] ?: return false
        val up = listOfNotNull(frame[Joint.LEFT_WRIST], frame[Joint.RIGHT_WRIST]).count { it.y < nose.y }
        return if (both) up >= 2 else up >= 1
    }

    fun forMove(move: Move): PoseTracker? = when (move) {
        // os que já têm detector próprio do treino livre
        Move.PUSHUP, Move.SQUAT, Move.LUNGE, Move.SITUP, Move.JUMPING_JACK, Move.BURPEE, Move.PLANK, Move.WALL_SIT ->
            move.camera?.let { PoseTracker.forExercise(it) }

        // flexões: cotovelo
        Move.WALL_PUSHUP -> BendRepTracker(Bend.ELBOW, contracted = 110f, extended = 155f, partial = 135f)
        Move.INCLINE_PUSHUP -> BendRepTracker(Bend.ELBOW, 100f, 150f, 130f, position = { s -> if (s.torsoLean() < 25f) Cue.GET_READY else null })
        Move.KNEE_PUSHUP, Move.DIAMOND_PUSHUP -> BendRepTracker(Bend.ELBOW, 100f, 150f, 130f, position = lying)
        Move.CHAIR_DIP -> BendRepTracker(Bend.ELBOW, 100f, 155f, 130f, position = standing)

        // pernas: joelho / quadril
        Move.CHAIR_SQUAT -> BendRepTracker(Bend.KNEE, 105f, 155f, 135f, position = standing)
        Move.JUMP_SQUAT -> BendRepTracker(Bend.KNEE, 110f, 160f, 140f, position = standing)
        Move.GLUTE_BRIDGE -> BendRepTracker(Bend.HIP, contracted = 160f, extended = 135f, partial = 150f, rising = true, position = lying)
        Move.CRUNCH -> BendRepTracker(Bend.HIP, 112f, 128f, 120f, position = lying)
        Move.SEATED_MARCH -> BendRepTracker(Bend.HIP, 70f, 84f, 77f, bothSides = true)

        // segurar posição
        Move.SIDE_PLANK -> PoseHoldTracker { f ->
            val s = f.bestSide(SidePart.SHOULDER, SidePart.HIP, SidePart.ANKLE) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            when {
                Geometry.leanFromVertical(s.ankle, s.shoulder) < 45f -> Cue.GET_READY
                Geometry.angle(s.shoulder, s.hip, s.ankle) < 150f -> Cue.HIPS_UP
                else -> Cue.HOLD
            }
        }
        Move.MOUNTAIN_CLIMBER -> PoseHoldTracker { f ->
            val s = f.bestSide(SidePart.SHOULDER, SidePart.ELBOW, SidePart.WRIST, SidePart.HIP) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.torsoLean() > 45f && s.bend(Bend.ELBOW) > 140f) Cue.HOLD else Cue.GET_READY
        }
        Move.FORWARD_FOLD -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            when {
                s.bend(Bend.KNEE) < 120f -> Cue.STAND
                s.bend(Bend.HIP) > 100f -> Cue.GO_DOWN
                else -> Cue.HOLD
            }
        }
        Move.SEATED_FORWARD -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.bend(Bend.HIP) < 70f) Cue.HOLD else Cue.GO_DOWN
        }
        Move.OVERHEAD_REACH, Move.SEATED_REACH -> PoseHoldTracker { f -> if (handsUp(f, both = true)) Cue.HOLD else Cue.EXTEND }
        Move.SIDE_BEND -> PoseHoldTracker { f ->
            val s = f.bestSide(SidePart.SHOULDER, SidePart.HIP) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            when {
                !handsUp(f, both = false) -> Cue.EXTEND
                s.torsoLean() < 10f -> Cue.GO_DOWN
                else -> Cue.HOLD
            }
        }
        Move.QUAD_STRETCH -> PoseHoldTracker { f ->
            val l = f.side(true, *Bend.KNEE.parts)
            val r = f.side(false, *Bend.KNEE.parts)
            if (l == null || r == null) return@PoseHoldTracker Cue.NOT_VISIBLE
            val knees = listOf(l.bend(Bend.KNEE), r.bend(Bend.KNEE)).sorted()
            if (knees[0] < 75f && knees[1] > 145f) Cue.HOLD else Cue.GET_READY
        }
        Move.HAMSTRING_STRETCH -> PoseHoldTracker { f ->
            val legs = listOfNotNull(f.side(true, *Bend.KNEE.parts, SidePart.SHOULDER), f.side(false, *Bend.KNEE.parts, SidePart.SHOULDER))
            if (legs.isEmpty()) return@PoseHoldTracker Cue.NOT_VISIBLE
            when {
                legs.first().torsoLean() < 50f -> Cue.FLOOR
                legs.any { it.bend(Bend.HIP) < 120f && it.bend(Bend.KNEE) > 140f } -> Cue.HOLD
                else -> Cue.EXTEND
            }
        }
        Move.HIP_FLEXOR_STRETCH -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.torsoLean() < 35f && s.bend(Bend.KNEE) < 135f) Cue.HOLD else Cue.CHEST_UP
        }
        Move.CHILD_POSE -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.bend(Bend.KNEE) < 75f && s.bend(Bend.HIP) < 75f) Cue.HOLD else Cue.GO_DOWN
        }
        Move.KNEES_TO_CHEST -> PoseHoldTracker { f ->
            val s = f.bestSide(*Bend.HIP.parts) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            when {
                s.torsoLean() < 50f -> Cue.FLOOR
                s.bend(Bend.HIP) > 85f -> Cue.PULL
                else -> Cue.HOLD
            }
        }
        Move.SPINAL_TWIST -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.torsoLean() > 50f && s.bend(Bend.KNEE) < 130f) Cue.HOLD else Cue.FLOOR
        }
        Move.BUTTERFLY -> PoseHoldTracker { f ->
            val s = body(f) ?: return@PoseHoldTracker Cue.NOT_VISIBLE
            if (s.torsoLean() < 40f && s.bend(Bend.KNEE) < 75f) Cue.HOLD else Cue.GET_READY
        }

        // aquecimento, pescoço e movimentos que a câmera não confere bem: só no ritmo
        else -> null
    }
}
