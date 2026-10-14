package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.DistractionApp
import com.pushlife.app.core.ExerciseFrequency
import com.pushlife.app.core.Goal
import com.pushlife.app.core.Profile
import com.pushlife.app.core.QuitReason
import com.pushlife.app.core.TriedMethod
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
            putString(KEY_USERNAME, profile.username)
            putStringSet(KEY_GOALS, profile.goals.map { it.name }.toSet())
            putFloat(KEY_PHONE_HOURS, profile.phoneHours)
            putFloat(KEY_TARGET_HOURS, profile.targetHours)
            putString(KEY_FREQUENCY, profile.exerciseFrequency?.name)
            putInt(KEY_DAILY_GOAL, profile.dailyGoal)
            putString(KEY_REMINDER, profile.reminder?.toString())
            putInt(KEY_AVATAR_COLOR, profile.avatarColor)
            putBoolean(KEY_NOTIFY_STREAK, profile.notifyStreak)
            putBoolean(KEY_NOTIFY_WEEKLY, profile.notifyWeekly)
            putBoolean(KEY_NOTIFY_LOW, profile.notifyLowTime)
            putStringSet(KEY_DISTRACTIONS, profile.distractions.map { it.name }.toSet())
            putStringSet(KEY_REASONS, profile.quitReasons.map { it.name }.toSet())
            putStringSet(KEY_TRIED, profile.triedMethods.map { it.name }.toSet())
        }
        _state.value = profile
    }

    private fun load(): Profile {
        val defaults = Profile()
        return Profile(
            onboardingDone = prefs.getBoolean(KEY_DONE, false),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            goals = prefs.getStringSet(KEY_GOALS, emptySet()).orEmpty()
                .mapNotNull { name -> Goal.entries.firstOrNull { it.name == name } }.toSet(),
            phoneHours = prefs.getFloat(KEY_PHONE_HOURS, defaults.phoneHours),
            targetHours = prefs.getFloat(KEY_TARGET_HOURS, defaults.targetHours),
            exerciseFrequency = prefs.getString(KEY_FREQUENCY, null)
                ?.let { name -> ExerciseFrequency.entries.firstOrNull { it.name == name } },
            dailyGoal = prefs.getInt(KEY_DAILY_GOAL, defaults.dailyGoal),
            reminder = prefs.getString(KEY_REMINDER, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            avatarColor = prefs.getInt(KEY_AVATAR_COLOR, 0),
            notifyStreak = prefs.getBoolean(KEY_NOTIFY_STREAK, true),
            notifyWeekly = prefs.getBoolean(KEY_NOTIFY_WEEKLY, true),
            notifyLowTime = prefs.getBoolean(KEY_NOTIFY_LOW, true),
            distractions = enums(KEY_DISTRACTIONS, DistractionApp.entries),
            quitReasons = enums(KEY_REASONS, QuitReason.entries),
            triedMethods = enums(KEY_TRIED, TriedMethod.entries),
        )
    }

    private fun <E : Enum<E>> enums(key: String, all: List<E>): Set<E> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { name -> all.firstOrNull { it.name == name } }.toSet()



    private companion object {
        const val KEY_DONE = "onboarding_done"
        const val KEY_NAME = "name"
        const val KEY_USERNAME = "username"
        const val KEY_GOALS = "goals"
        const val KEY_PHONE_HOURS = "phone_hours"
        const val KEY_TARGET_HOURS = "target_hours"
        const val KEY_FREQUENCY = "exercise_frequency"
        const val KEY_DAILY_GOAL = "daily_goal"
        const val KEY_REMINDER = "reminder"
        const val KEY_AVATAR_COLOR = "avatar_color"
        const val KEY_NOTIFY_STREAK = "notify_streak"
        const val KEY_NOTIFY_WEEKLY = "notify_weekly"
        const val KEY_NOTIFY_LOW = "notify_low_time"
        const val KEY_DISTRACTIONS = "distractions"
        const val KEY_REASONS = "quit_reasons"
        const val KEY_TRIED = "tried_methods"
    }
}
