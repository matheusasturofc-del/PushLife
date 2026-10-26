package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.BankState
import com.pushlife.app.core.Credit
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.Rules
import com.pushlife.app.core.TimeBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

// saldo de tempo e histórico, tudo salvo no celular
class BankRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bank", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<BankState> = _state.asStateFlow()

    @Synchronized
    fun addExercise(exercise: Exercise, amount: Int, today: LocalDate = LocalDate.now()): Credit {
        val credit = TimeBank.addExercise(_state.value, exercise, amount, today)
        save(credit.state)
        return credit
    }

    @Synchronized
    fun addBonus(seconds: Long): Credit {
        val credit = TimeBank.addBonus(_state.value, seconds)
        save(credit.state)
        return credit
    }

    @Synchronized
    fun spend(seconds: Long, today: LocalDate = LocalDate.now()) {
        save(TimeBank.spend(_state.value, seconds, today))
    }

    // grátis por dia mudou (a meta de tela mudou)
    @Synchronized
    fun setFreeDaily(seconds: Long) {
        if (_state.value.freeDailySeconds != seconds) save(_state.value.copy(freeDailySeconds = seconds))
    }

    // gasta uma emergência; false se acabaram as de hoje
    @Synchronized
    fun emergency(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): Boolean {
        val next = TimeBank.emergency(_state.value, today, nowMs) ?: return false
        save(next)
        return true
    }

    private fun load(): BankState {
        // versão antiga só guardava flexões (reps_by_day / debt_reps)
        val legacyReps = decodeDayInts(prefs.getString(KEY_LEGACY_REPS, "").orEmpty())
        val counts = prefs.getString(KEY_COUNTS, null)?.let(::decodeCounts)
            ?: legacyReps.mapValues { mapOf(Exercise.PUSHUP to it.value) }
        val effort = prefs.getString(KEY_EFFORT, null)?.let { decodeDayInts(it).mapValues { e -> e.value.toLong() } }
            ?: legacyReps.mapValues { it.value * 60L }
        return BankState(
            earnedSeconds = prefs.getLong(KEY_EARNED, 0),
            freeUsedSeconds = prefs.getLong(KEY_FREE_USED, 0),
            freeDay = prefs.getString(KEY_FREE_DAY, null)?.let(LocalDate::parse),
            countsByDay = counts,
            effortByDay = effort,
            debtSeconds = if (prefs.contains(KEY_DEBT_SECONDS)) prefs.getLong(KEY_DEBT_SECONDS, 0) else prefs.getInt(KEY_LEGACY_DEBT, 0) * 60L,
            emergencyDay = prefs.getString(KEY_EMERGENCY_DAY, null)?.let(LocalDate::parse),
            emergenciesUsed = prefs.getInt(KEY_EMERGENCIES_USED, 0),
            emergencyUntilMs = prefs.getLong(KEY_EMERGENCY_UNTIL, 0),
            freeDailySeconds = prefs.getLong(KEY_FREE_DAILY, Rules.FREE_SECONDS_PER_DAY),
        )
    }

    private fun save(state: BankState) {
        prefs.edit {
            putLong(KEY_EARNED, state.earnedSeconds)
            putLong(KEY_FREE_USED, state.freeUsedSeconds)
            putString(KEY_FREE_DAY, state.freeDay?.toString())
            putString(KEY_COUNTS, encodeCounts(state.countsByDay))
            putString(KEY_EFFORT, state.effortByDay.entries.joinToString(";") { "${it.key}=${it.value}" })
            putLong(KEY_DEBT_SECONDS, state.debtSeconds)
            putString(KEY_EMERGENCY_DAY, state.emergencyDay?.toString())
            putInt(KEY_EMERGENCIES_USED, state.emergenciesUsed)
            putLong(KEY_EMERGENCY_UNTIL, state.emergencyUntilMs)
            putLong(KEY_FREE_DAILY, state.freeDailySeconds)
            remove(KEY_LEGACY_REPS)
            remove(KEY_LEGACY_DEBT)
        }
        _state.value = state
    }

    private companion object {
        const val KEY_EARNED = "earned_seconds"
        const val KEY_FREE_USED = "free_used_seconds"
        const val KEY_FREE_DAY = "free_day"
        const val KEY_COUNTS = "counts_by_day"
        const val KEY_EFFORT = "effort_by_day"
        const val KEY_DEBT_SECONDS = "debt_seconds"
        const val KEY_EMERGENCY_DAY = "emergency_day"
        const val KEY_EMERGENCIES_USED = "emergencies_used"
        const val KEY_EMERGENCY_UNTIL = "emergency_until_ms"
        const val KEY_FREE_DAILY = "free_daily_seconds"
        const val KEY_LEGACY_REPS = "reps_by_day"
        const val KEY_LEGACY_DEBT = "debt_reps"

        // Formato: "2026-10-13=PUSHUP:20,SQUAT:5;2026-10-12=PUSHUP:10"
        fun encodeCounts(map: Map<LocalDate, Map<Exercise, Int>>) = map.entries.joinToString(";") { (day, counts) ->
            "$day=" + counts.entries.joinToString(",") { "${it.key.name}:${it.value}" }
        }

        fun decodeCounts(text: String): Map<LocalDate, Map<Exercise, Int>> = text.split(';')
            .mapNotNull { entry ->
                val (day, list) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val counts = list.split(',').mapNotNull { pair ->
                    val (name, n) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    val exercise = Exercise.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                    n.toIntOrNull()?.let { exercise to it }
                }.toMap()
                runCatching { LocalDate.parse(day) to counts }.getOrNull()
            }
            .toMap()

        // Formato: "2026-10-13=25;2026-10-12=10"
        fun decodeDayInts(text: String): Map<LocalDate, Int> = text.split(';')
            .mapNotNull { entry ->
                val (day, value) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { LocalDate.parse(day) to value.toInt() }.getOrNull()
            }
            .toMap()
    }
}
