package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.ExerciseFrequency
import com.pushlife.app.core.Goal
import com.pushlife.app.core.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/** Perfil e respostas das boas-vindas, guardados no próprio celular. */
class ProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Profile> = _state.asStateFlow()

    @Synchronized
    fun save(profile: Profile) {
        prefs.edit {
            putBoolean(KEY_DONE, profile.onboardingDone)
            putString(KEY_NAME, profile.name)
            putStringSet(KEY_GOALS, profile.goals.map { it.name }.toSet())
            putFloat(KEY_PHONE_HOURS, profile.phoneHours)
            putFloat(KEY_TARGET_HOURS, profile.targetHours)
            putString(KEY_FREQUENCY, profile.exerciseFrequency?.name)
            putInt(KEY_DAILY_GOAL, profile.dailyGoal)
            putString(KEY_REMINDER, profile.reminder?.toString())
        }
        _state.value = profile
    }

    private fun load(): Profile {
        val defaults = Profile()
        return Profile(
            onboardingDone = prefs.getBoolean(KEY_DONE, false),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            goals = prefs.getStringSet(KEY_GOALS, emptySet()).orEmpty()
                .mapNotNull { name -> Goal.entries.firstOrNull { it.name == name } }.toSet(),
            phoneHours = prefs.getFloat(KEY_PHONE_HOURS, defaults.phoneHours),
            targetHours = prefs.getFloat(KEY_TARGET_HOURS, defaults.targetHours),
            exerciseFrequency = prefs.getString(KEY_FREQUENCY, null)
                ?.let { name -> ExerciseFrequency.entries.firstOrNull { it.name == name } },
            dailyGoal = prefs.getInt(KEY_DAILY_GOAL, defaults.dailyGoal),
            reminder = prefs.getString(KEY_REMINDER, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        )
    }

    private companion object {
        const val KEY_DONE = "onboarding_done"
        const val KEY_NAME = "name"
        const val KEY_GOALS = "goals"
        const val KEY_PHONE_HOURS = "phone_hours"
        const val KEY_TARGET_HOURS = "target_hours"
        const val KEY_FREQUENCY = "exercise_frequency"
        const val KEY_DAILY_GOAL = "daily_goal"
        const val KEY_REMINDER = "reminder"
    }
}
