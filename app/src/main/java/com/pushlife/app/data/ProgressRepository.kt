package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.progress.Achievement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class ProgressState(
    /** Dias em que a meta diária foi batida (registrado na hora, com a meta daquele dia). */
    val goalMetDays: Set<LocalDate> = emptySet(),
    /** Semanas (segunda-feira) cujo desafio já foi resgatado. */
    val claimedWeeks: Set<LocalDate> = emptySet(),
    /** Conquistas que a pessoa já viu (as novas aparecem no fim do treino). */
    val seenAchievements: Set<Achievement> = emptySet(),
    val seenLevel: Int = 1,
    /** Última vez que a meta diária foi ajustada pela evolução automática. */
    val lastGoalAdjust: LocalDate? = null,
)

class ProgressRepository(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    @Synchronized
    fun markGoalMet(day: LocalDate) {
        if (day !in _state.value.goalMetDays) save(_state.value.copy(goalMetDays = _state.value.goalMetDays + day))
    }

    @Synchronized
    fun markClaimed(weekStart: LocalDate) = save(_state.value.copy(claimedWeeks = _state.value.claimedWeeks + weekStart))

    @Synchronized
    fun markSeen(achievements: Set<Achievement>, level: Int) =
        save(_state.value.copy(seenAchievements = _state.value.seenAchievements + achievements, seenLevel = maxOf(level, _state.value.seenLevel)))

    @Synchronized
    fun markGoalAdjusted(day: LocalDate) = save(_state.value.copy(lastGoalAdjust = day))

    private fun load() = ProgressState(
        goalMetDays = days(prefs.getStringSet(KEY_GOAL_DAYS, emptySet())),
        claimedWeeks = days(prefs.getStringSet(KEY_CLAIMED, emptySet())),
        seenAchievements = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
            .mapNotNull { name -> Achievement.entries.firstOrNull { it.name == name } }.toSet(),
        seenLevel = prefs.getInt(KEY_SEEN_LEVEL, 1),
        lastGoalAdjust = prefs.getString(KEY_GOAL_ADJUST, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    private fun save(state: ProgressState) {
        prefs.edit {
            putStringSet(KEY_GOAL_DAYS, state.goalMetDays.map { it.toString() }.toSet())
            putStringSet(KEY_CLAIMED, state.claimedWeeks.map { it.toString() }.toSet())
            putStringSet(KEY_SEEN, state.seenAchievements.map { it.name }.toSet())
            putInt(KEY_SEEN_LEVEL, state.seenLevel)
            putString(KEY_GOAL_ADJUST, state.lastGoalAdjust?.toString())
        }
        _state.value = state
    }

    private fun days(raw: Set<String>?): Set<LocalDate> =
        raw.orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    private companion object {
        const val KEY_GOAL_DAYS = "goal_met_days"
        const val KEY_CLAIMED = "claimed_weeks"
        const val KEY_SEEN = "seen_achievements"
        const val KEY_SEEN_LEVEL = "seen_level"
        const val KEY_GOAL_ADJUST = "last_goal_adjust"
    }
}
