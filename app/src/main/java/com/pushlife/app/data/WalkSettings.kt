package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit

// caminhada: confirmar os passos com o GPS (escolha da pessoa)
object WalkSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("walk", Context.MODE_PRIVATE)

    fun useGps(context: Context) = prefs(context).getBoolean(KEY_GPS, false)

    fun setUseGps(context: Context, on: Boolean) = prefs(context).edit { putBoolean(KEY_GPS, on) }

    private const val KEY_GPS = "use_gps"
}
