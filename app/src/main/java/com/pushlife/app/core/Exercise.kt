package com.pushlife.app.core

/** Como o app acompanha cada exercício. */
enum class Tracking { FACE, POSE, STEPS }

/** Em que unidade o exercício é contado. */
enum class ExerciseUnit { REPS, SECONDS, STEPS }

/**
 * Os exercícios do app e quanto tempo de tela cada unidade vale.
 * Referência: 1 flexão = 1 minuto; os outros seguem o esforço relativo.
 */
enum class Exercise(val secondsPerUnit: Double, val unit: ExerciseUnit, val tracking: Tracking) {
    PUSHUP(60.0, ExerciseUnit.REPS, Tracking.FACE),
    SQUAT(30.0, ExerciseUnit.REPS, Tracking.POSE),
    SITUP(30.0, ExerciseUnit.REPS, Tracking.POSE),
    JUMPING_JACK(15.0, ExerciseUnit.REPS, Tracking.POSE),
    PLANK(3.0, ExerciseUnit.SECONDS, Tracking.POSE),
    PULLUP(120.0, ExerciseUnit.REPS, Tracking.POSE),
    BURPEE(90.0, ExerciseUnit.REPS, Tracking.POSE),
    WALK(0.6, ExerciseUnit.STEPS, Tracking.STEPS),
    ;

    /** Tempo de tela (s) que [amount] unidades valem. */
    fun valueOf(amount: Int): Long = Math.round(amount * secondsPerUnit)
}
