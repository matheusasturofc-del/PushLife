package com.pushlife.app.core.packs

import kotlin.math.roundToInt

enum class PackCategory { STRENGTH, CHALLENGE, STRETCH }

// como o pack mede a evolução a cada 2 semanas
enum class TestType {
    MAX_REPS, // máximo de repetições seguidas (vale a variação que a pessoa faz hoje)
    REPS_IN_60, // quantas em 1 minuto
    MAX_HOLD, // quanto tempo segura
    REACH, // até onde a mão chega na flexão do tronco (1 = joelho … 5 = chão)
}

data class PackTest(val type: TestType, val slot: Slot)

// degrau da escada: a partir do nível [from] usa esse movimento com base + inc por nível
data class Rung(val move: Move, val from: Int, val base: Int, val inc: Float) {
    fun amountAt(level: Float): Int = (base + inc * (level - from).coerceAtLeast(0f)).roundToInt().coerceAtLeast(1)
}

// um exercício do treino; com vários degraus, troca pela variação mais difícil quando o nível sobe
data class Slot(val rungs: List<Rung>, val sets: Int = 1) {
    fun rungAt(level: Float): Rung = rungs.last { it.from <= level.toInt().coerceAtLeast(rungs.first().from) }
}

private fun fixed(move: Move, base: Int, inc: Float = 0f, sets: Int = 1) = Slot(listOf(Rung(move, 1, base, inc)), sets)
private fun ladder(vararg rungs: Rung, sets: Int = 1) = Slot(rungs.toList(), sets)

enum class PackId { ARMS, LEGS, CORE, FULL, CHALLENGE_10, CHALLENGE_100, STRETCH_MORNING, STRETCH_NIGHT, STRETCH_NECK, STRETCH_CHAIR }

data class Pack(
    val id: PackId,
    val category: PackCategory,
    val weeks: Int,
    val daysPerWeek: Int,
    val warmup: List<Slot>,
    val main: List<Slot>,
    val cooldown: List<Slot>,
    val restSeconds: Int,
    val test: PackTest,
    // circuito: faz um de cada e repete a volta; senão faz todas as séries de um antes do próximo
    val circuit: Boolean = false,
    val rounds: Int = 1,
) {
    val bonusSeconds: Long get() = if (category == PackCategory.STRETCH) PackRules.STRETCH_BONUS else PackRules.WORKOUT_BONUS
}

object PackRules {
    const val MIN_LEVEL = 1f
    const val MAX_LEVEL = 20f
    const val TEST_EVERY_DAYS = 14L
    const val WORKOUT_BONUS = 15 * 60L // treino de força completo
    const val STRETCH_BONUS = 10 * 60L // alongamento completo
    const val WEEK_BONUS = 30 * 60L // semana do programa completa
}

// as variações de flexão, da parede até a diamante
private fun pushLadder(sets: Int, wall: Int = 1, incline: Int = 5, knee: Int = 9, floor: Int = 13, diamond: Int = 18, floorBase: Int = 5) = ladder(
    Rung(Move.WALL_PUSHUP, wall, 8, 1f),
    Rung(Move.INCLINE_PUSHUP, incline, 6, 1f),
    Rung(Move.KNEE_PUSHUP, knee, 6, 1f),
    Rung(Move.PUSHUP, floor, floorBase, 1f),
    Rung(Move.DIAMOND_PUSHUP, diamond, 5, 1f),
    sets = sets,
)

private fun squatLadder(sets: Int) = ladder(
    Rung(Move.CHAIR_SQUAT, 1, 8, 1f),
    Rung(Move.SQUAT, 7, 10, 1f),
    Rung(Move.JUMP_SQUAT, 16, 8, 1f),
    sets = sets,
)

object PackCatalog {
    val all: List<Pack> = listOf(
        Pack(
            PackId.ARMS, PackCategory.STRENGTH, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.ARM_CIRCLES, 30), fixed(Move.SHOULDER_ROLL, 10), fixed(Move.WALL_PUSHUP, 8)),
            main = listOf(pushLadder(sets = 3), fixed(Move.CHAIR_DIP, 5, 0.5f, sets = 3), fixed(Move.PLANK, 15, 3f, sets = 2)),
            cooldown = listOf(fixed(Move.CHEST_OPENER, 30), fixed(Move.OVERHEAD_REACH, 20), fixed(Move.CHILD_POSE, 30)),
            restSeconds = 60,
            test = PackTest(TestType.MAX_REPS, pushLadder(sets = 1)),
        ),
        Pack(
            PackId.LEGS, PackCategory.STRENGTH, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.MARCH, 45), fixed(Move.CHAIR_SQUAT, 8), fixed(Move.CALF_RAISE, 10)),
            main = listOf(
                squatLadder(sets = 3),
                fixed(Move.GLUTE_BRIDGE, 10, 1f, sets = 3),
                ladder(Rung(Move.CALF_RAISE, 1, 12, 1f), Rung(Move.LUNGE, 6, 6, 0.5f), sets = 3),
                fixed(Move.WALL_SIT, 20, 3f, sets = 2),
            ),
            cooldown = listOf(fixed(Move.QUAD_STRETCH, 25), fixed(Move.HAMSTRING_STRETCH, 25), fixed(Move.HIP_FLEXOR_STRETCH, 25)),
            restSeconds = 60,
            test = PackTest(TestType.REPS_IN_60, squatLadder(sets = 1)),
        ),
        Pack(
            PackId.CORE, PackCategory.STRENGTH, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.MARCH, 30), fixed(Move.CAT_COW, 8)),
            main = listOf(
                ladder(Rung(Move.CRUNCH, 1, 10, 1f), Rung(Move.SITUP, 10, 10, 1f), sets = 3),
                fixed(Move.DEAD_BUG, 6, 0.5f, sets = 2),
                fixed(Move.BIRD_DOG, 6, 0.5f, sets = 2),
                fixed(Move.PLANK, 15, 3f, sets = 3),
                fixed(Move.SIDE_PLANK, 10, 2f, sets = 2),
            ),
            cooldown = listOf(fixed(Move.CHILD_POSE, 30), fixed(Move.KNEES_TO_CHEST, 30), fixed(Move.SPINAL_TWIST, 25)),
            restSeconds = 45,
            test = PackTest(TestType.MAX_HOLD, fixed(Move.PLANK, 15)),
        ),
        Pack(
            PackId.FULL, PackCategory.STRENGTH, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.MARCH, 45), fixed(Move.ARM_CIRCLES, 30)),
            main = listOf(
                fixed(Move.JUMPING_JACK, 20, 2f),
                squatLadder(sets = 1),
                pushLadder(sets = 1),
                fixed(Move.MOUNTAIN_CLIMBER, 20, 2f),
                fixed(Move.BURPEE, 4, 0.5f),
            ),
            cooldown = listOf(fixed(Move.FORWARD_FOLD, 30), fixed(Move.QUAD_STRETCH, 20), fixed(Move.CHEST_OPENER, 20)),
            restSeconds = 30,
            test = PackTest(TestType.REPS_IN_60, fixed(Move.JUMPING_JACK, 20)),
            circuit = true,
            rounds = 3,
        ),
        Pack(
            PackId.CHALLENGE_10, PackCategory.CHALLENGE, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.ARM_CIRCLES, 30), fixed(Move.WALL_PUSHUP, 8)),
            main = listOf(pushLadder(sets = 4, incline = 4, knee = 8, floor = 13, diamond = 99, floorBase = 3), fixed(Move.PLANK, 15, 2f)),
            cooldown = listOf(fixed(Move.CHEST_OPENER, 30), fixed(Move.CHILD_POSE, 30)),
            restSeconds = 60,
            test = PackTest(TestType.MAX_REPS, pushLadder(sets = 1, incline = 4, knee = 8, floor = 13, diamond = 99, floorBase = 3)),
        ),
        Pack(
            PackId.CHALLENGE_100, PackCategory.CHALLENGE, weeks = 6, daysPerWeek = 3,
            warmup = listOf(fixed(Move.ARM_CIRCLES, 30), fixed(Move.KNEE_PUSHUP, 8)),
            main = listOf(fixed(Move.PUSHUP, 6, 2f, sets = 5)),
            cooldown = listOf(fixed(Move.CHEST_OPENER, 30), fixed(Move.CHILD_POSE, 30)),
            restSeconds = 75,
            test = PackTest(TestType.MAX_REPS, fixed(Move.PUSHUP, 10)),
        ),
        Pack(
            PackId.STRETCH_MORNING, PackCategory.STRETCH, weeks = 4, daysPerWeek = 7,
            warmup = emptyList(),
            main = listOf(
                fixed(Move.OVERHEAD_REACH, 20, 0.5f), fixed(Move.SIDE_BEND, 15, 0.5f), fixed(Move.CAT_COW, 8),
                fixed(Move.FORWARD_FOLD, 20, 1f), fixed(Move.HIP_FLEXOR_STRETCH, 20, 1f), fixed(Move.SHOULDER_ROLL, 10),
                fixed(Move.NECK_TILT, 15, 0.5f),
            ),
            cooldown = emptyList(),
            restSeconds = 5,
            test = PackTest(TestType.REACH, fixed(Move.FORWARD_FOLD, 20)),
        ),
        Pack(
            PackId.STRETCH_NIGHT, PackCategory.STRETCH, weeks = 4, daysPerWeek = 7,
            warmup = emptyList(),
            main = listOf(
                fixed(Move.CHILD_POSE, 30, 1f), fixed(Move.CAT_COW, 6), fixed(Move.KNEES_TO_CHEST, 30, 1f),
                fixed(Move.SPINAL_TWIST, 25, 1f), fixed(Move.BUTTERFLY, 30, 1f), fixed(Move.HAMSTRING_STRETCH, 25, 1f),
            ),
            cooldown = emptyList(),
            restSeconds = 5,
            test = PackTest(TestType.REACH, fixed(Move.FORWARD_FOLD, 20)),
        ),
        Pack(
            PackId.STRETCH_NECK, PackCategory.STRETCH, weeks = 4, daysPerWeek = 7,
            warmup = emptyList(),
            main = listOf(
                fixed(Move.CHIN_TUCK, 10), fixed(Move.NECK_TILT, 20, 0.5f), fixed(Move.SHOULDER_ROLL, 10),
                fixed(Move.CHEST_OPENER, 30, 1f), fixed(Move.CAT_COW, 8), fixed(Move.OVERHEAD_REACH, 20, 0.5f),
            ),
            cooldown = emptyList(),
            restSeconds = 5,
            test = PackTest(TestType.REACH, fixed(Move.FORWARD_FOLD, 20)),
        ),
        Pack(
            PackId.STRETCH_CHAIR, PackCategory.STRETCH, weeks = 4, daysPerWeek = 7,
            warmup = emptyList(),
            main = listOf(
                fixed(Move.SEATED_MARCH, 20), fixed(Move.SHOULDER_ROLL, 10), fixed(Move.NECK_TILT, 15, 0.5f),
                fixed(Move.SEATED_REACH, 20, 0.5f), fixed(Move.SEATED_TWIST, 20, 0.5f), fixed(Move.SEATED_FORWARD, 20, 0.5f),
                fixed(Move.CHIN_TUCK, 8),
            ),
            cooldown = emptyList(),
            restSeconds = 5,
            test = PackTest(TestType.REACH, fixed(Move.SEATED_FORWARD, 20)),
        ),
    )

    fun get(id: PackId): Pack = all.first { it.id == id }

    fun byCategory(category: PackCategory): List<Pack> = all.filter { it.category == category }
}

enum class Phase { WARMUP, MAIN, COOLDOWN }

// um passo do treino: faz [amount] e depois descansa [restAfter] s
data class WorkoutStep(val move: Move, val amount: Int, val phase: Phase, val set: Int, val sets: Int, val restAfter: Int)

object Workouts {
    fun build(pack: Pack, level: Float): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        // aquecimento e volta à calma: sem descanso, só troca de movimento
        fun easy(slots: List<Slot>, phase: Phase) = slots.forEach { slot ->
            val rung = slot.rungAt(level)
            steps += WorkoutStep(rung.move, rung.amountAt(level), phase, 1, 1, 0)
        }
        easy(pack.warmup, Phase.WARMUP)
        if (pack.circuit) {
            for (round in 1..pack.rounds) pack.main.forEach { slot ->
                val rung = slot.rungAt(level)
                steps += WorkoutStep(rung.move, rung.amountAt(level), Phase.MAIN, round, pack.rounds, pack.restSeconds)
            }
        } else {
            pack.main.forEach { slot ->
                val rung = slot.rungAt(level)
                for (set in 1..slot.sets) steps += WorkoutStep(rung.move, rung.amountAt(level), Phase.MAIN, set, slot.sets, pack.restSeconds)
            }
        }
        // sem descanso depois do último exercício principal
        val lastMain = steps.indexOfLast { it.phase == Phase.MAIN }
        if (lastMain >= 0) steps[lastMain] = steps[lastMain].copy(restAfter = 0)
        easy(pack.cooldown, Phase.COOLDOWN)
        return steps
    }

    // duração estimada em minutos (arredonda pra cima)
    fun minutes(steps: List<WorkoutStep>): Int {
        val seconds = steps.sumOf { it.move.secondsFor(it.amount) + it.restAfter + 5 }
        return (seconds + 59) / 60
    }
}
