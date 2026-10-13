package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlockSettings(
    val enabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
)

/** Quais apps ficam bloqueados e se a proteção está ligada. */
class BlockSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("blocking", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        BlockSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            blockedPackages = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet(),
        ),
    )
    val state: StateFlow<BlockSettings> = _state.asStateFlow()

    @Synchronized
    fun setEnabled(enabled: Boolean) = save(_state.value.copy(enabled = enabled))

    @Synchronized
    fun toggle(packageName: String) {
        val current = _state.value.blockedPackages
        save(_state.value.copy(blockedPackages = if (packageName in current) current - packageName else current + packageName))
    }

    private fun save(settings: BlockSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putStringSet(KEY_PACKAGES, settings.blockedPackages)
        }
        _state.value = settings
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PACKAGES = "blocked_packages"
    }
}
