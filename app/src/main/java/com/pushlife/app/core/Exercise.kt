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
    PUSHUP(60.0, ExerciseUnit.REPS, Tracking.POSE),
    SQUAT(30.0, ExerciseUnit.REPS, Tracking.POSE),
    LUNGE(30.0, ExerciseUnit.REPS, Tracking.POSE),
    SITUP(30.0, ExerciseUnit.REPS, Tracking.POSE),
    JUMPING_JACK(15.0, ExerciseUnit.REPS, Tracking.POSE),
    PLANK(3.0, ExerciseUnit.SECONDS, Tracking.POSE),
    WALL_SIT(3.0, ExerciseUnit.SECONDS, Tracking.POSE),
    PULLUP(120.0, ExerciseUnit.REPS, Tracking.POSE),
    BURPEE(90.0, ExerciseUnit.REPS, Tracking.POSE),
    WALK(0.6, ExerciseUnit.STEPS, Tracking.STEPS),
    ;

    /** Tempo de tela (s) que [amount] unidades valem. */
    fun valueOf(amount: Int): Long = Math.round(amount * secondsPerUnit)
}

/** Os exercícios sugeridos na tela inicial e na tela de bloqueio. */
object Recommendations {
    private val DEFAULTS = listOf(Exercise.PUSHUP, Exercise.SITUP)

    /**
     * Os [n] mais praticados: primeiro em quantos dias a pessoa fez, depois em quanto tempo de tela
     * valeram. Sem histórico (ou com pouco), completa com flexão e abdominal.
     */
    fun top(countsByDay: Map<java.time.LocalDate, Map<Exercise, Int>>, n: Int = 2): List<Exercise> {
        val days = mutableMapOf<Exercise, Int>()
        val value = mutableMapOf<Exercise, Long>()
        for (counts in countsByDay.values) {
            for ((exercise, amount) in counts) {
                if (amount <= 0) continue
                days[exercise] = (days[exercise] ?: 0) + 1
                value[exercise] = (value[exercise] ?: 0) + exercise.valueOf(amount)
            }
        }
        val practiced = days.keys.sortedWith(compareByDescending<Exercise> { days[it] }.thenByDescending { value[it] })
        return (practiced + DEFAULTS).distinct().take(n)
    }
}
