package com.pushlife.app.core.packs

import com.pushlife.app.core.Exercise

// repetições ou segurar por segundos
enum class MoveKind { REPS, HOLD }

// movimentos dos packs; os que têm camera contam sozinhos pela câmera
// difficulty compara variações do mesmo exercício (flexão normal = 1)
enum class Move(
    val kind: MoveKind,
    val camera: Exercise? = null,
    val perSide: Boolean = false,
    val difficulty: Float = 1f,
    val stretch: Boolean = false,
) {
    // aquecimento
    MARCH(MoveKind.HOLD),
    ARM_CIRCLES(MoveKind.HOLD),
    JUMPING_JACK(MoveKind.REPS, camera = Exercise.JUMPING_JACK),

    // braço e peito
    WALL_PUSHUP(MoveKind.REPS, difficulty = 0.4f),
    INCLINE_PUSHUP(MoveKind.REPS, difficulty = 0.6f),
    KNEE_PUSHUP(MoveKind.REPS, difficulty = 0.75f),
    PUSHUP(MoveKind.REPS, camera = Exercise.PUSHUP),
    DIAMOND_PUSHUP(MoveKind.REPS, difficulty = 1.3f),
    CHAIR_DIP(MoveKind.REPS),
    PLANK(MoveKind.HOLD, camera = Exercise.PLANK),

    // pernas e glúteos
    CHAIR_SQUAT(MoveKind.REPS, difficulty = 0.6f),
    SQUAT(MoveKind.REPS, camera = Exercise.SQUAT),
    JUMP_SQUAT(MoveKind.REPS, difficulty = 1.4f),
    LUNGE(MoveKind.REPS, camera = Exercise.LUNGE, perSide = true),
    WALL_SIT(MoveKind.HOLD, camera = Exercise.WALL_SIT),
    GLUTE_BRIDGE(MoveKind.REPS),
    CALF_RAISE(MoveKind.REPS),

    // abdômen e core
    CRUNCH(MoveKind.REPS, difficulty = 0.7f),
    SITUP(MoveKind.REPS, camera = Exercise.SITUP),
    DEAD_BUG(MoveKind.REPS, perSide = true),
    BIRD_DOG(MoveKind.REPS, perSide = true),
    SIDE_PLANK(MoveKind.HOLD, perSide = true),
    MOUNTAIN_CLIMBER(MoveKind.HOLD),
    BURPEE(MoveKind.REPS, camera = Exercise.BURPEE),

    // alongamentos
    NECK_TILT(MoveKind.HOLD, perSide = true, stretch = true),
    CHIN_TUCK(MoveKind.REPS, stretch = true),
    SHOULDER_ROLL(MoveKind.REPS, stretch = true),
    CHEST_OPENER(MoveKind.HOLD, stretch = true),
    CAT_COW(MoveKind.REPS, stretch = true),
    CHILD_POSE(MoveKind.HOLD, stretch = true),
    FORWARD_FOLD(MoveKind.HOLD, stretch = true),
    HAMSTRING_STRETCH(MoveKind.HOLD, perSide = true, stretch = true),
    QUAD_STRETCH(MoveKind.HOLD, perSide = true, stretch = true),
    HIP_FLEXOR_STRETCH(MoveKind.HOLD, perSide = true, stretch = true),
    SPINAL_TWIST(MoveKind.HOLD, perSide = true, stretch = true),
    KNEES_TO_CHEST(MoveKind.HOLD, stretch = true),
    OVERHEAD_REACH(MoveKind.HOLD, stretch = true),
    SIDE_BEND(MoveKind.HOLD, perSide = true, stretch = true),
    BUTTERFLY(MoveKind.HOLD, stretch = true),

    // na cadeira (qualquer idade)
    SEATED_MARCH(MoveKind.REPS, stretch = true),
    SEATED_TWIST(MoveKind.HOLD, perSide = true, stretch = true),
    SEATED_FORWARD(MoveKind.HOLD, stretch = true),
    SEATED_REACH(MoveKind.HOLD, stretch = true),
    ;

    // segundos estimados pra fazer essa quantidade (pra mostrar a duração do treino)
    fun secondsFor(amount: Int): Int {
        val one = if (kind == MoveKind.HOLD) amount else amount * 3
        return if (perSide) one * 2 else one
    }
}
