package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.BankState
import com.pushlife.app.core.Credit
import com.pushlife.app.core.TimeBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Guarda o saldo de tempo e as repetições no próprio celular. */
class BankRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bank", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<BankState> = _state.asStateFlow()

    @Synchronized
    fun addReps(reps: Int, today: LocalDate = LocalDate.now()): Credit {
        val credit = TimeBank.addReps(_state.value, reps, today)
        save(credit.state)
        return credit
    }

    @Synchronized
    fun spend(seconds: Long, today: LocalDate = LocalDate.now()) {
        save(TimeBank.spend(_state.value, seconds, today))
    }

    private fun load() = BankState(
        earnedSeconds = prefs.getLong(KEY_EARNED, 0),
        freeUsedSeconds = prefs.getLong(KEY_FREE_USED, 0),
        freeDay = prefs.getString(KEY_FREE_DAY, null)?.let(LocalDate::parse),
        repsByDay = decodeReps(prefs.getString(KEY_REPS, "").orEmpty()),
        totalReps = prefs.getInt(KEY_TOTAL, 0),
    )

    private fun save(state: BankState) {
        prefs.edit {
            putLong(KEY_EARNED, state.earnedSeconds)
            putLong(KEY_FREE_USED, state.freeUsedSeconds)
            putString(KEY_FREE_DAY, state.freeDay?.toString())
            putString(KEY_REPS, encodeReps(state.repsByDay))
            putInt(KEY_TOTAL, state.totalReps)
        }
        _state.value = state
    }

    private companion object {
        const val KEY_EARNED = "earned_seconds"
        const val KEY_FREE_USED = "free_used_seconds"
        const val KEY_FREE_DAY = "free_day"
        const val KEY_REPS = "reps_by_day"
        const val KEY_TOTAL = "total_reps"

        // Formato: "2026-10-13=25;2026-10-12=10"
        fun encodeReps(map: Map<LocalDate, Int>) = map.entries.joinToString(";") { "${it.key}=${it.value}" }

        fun decodeReps(text: String): Map<LocalDate, Int> = text.split(';')
            .mapNotNull { entry ->
                val (day, reps) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { LocalDate.parse(day) to reps.toInt() }.getOrNull()
            }
            .toMap()
    }
}
