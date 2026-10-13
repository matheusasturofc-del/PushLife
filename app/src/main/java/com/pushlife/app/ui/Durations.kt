package com.pushlife.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pushlife.app.R

/** "45 min", "2 h" ou "1 h 20 min". */
fun Context.formatDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> getString(R.string.duration_min, m)
        m == 0 -> getString(R.string.duration_h, h)
        else -> getString(R.string.duration_h_min, h, m)
    }
}

@Composable
fun formatDuration(seconds: Long): String = LocalContext.current.formatDuration(seconds)
