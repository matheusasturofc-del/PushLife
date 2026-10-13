package com.pushlife.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pushlife.app.R

/** "45 min", "2 h" ou "1 h 20 min". */
@Composable
fun formatDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_min, m)
        m == 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_h_min, h, m)
    }
}
