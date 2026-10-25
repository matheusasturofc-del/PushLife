package com.pushlife.app.blocking

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.TimeUnit

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    // média diária dos últimos 7 dias em ms (0 se não tiver acesso ao uso)
    val dailyAverageMs: Long,
)

object InstalledApps {
    // apps da tela inicial, mais usados primeiro. é lento, roda fora da main thread
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val averages = dailyAverages(context)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launcher, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                    dailyAverageMs = averages[pkg] ?: 0,
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.dailyAverageMs }.thenBy { it.label.lowercase() })
    }

    private fun dailyAverages(context: Context): Map<String, Long> {
        if (!Permissions.hasUsageAccess(context)) return emptyMap()
        val now = System.currentTimeMillis()
        val days = 7
        return context.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(days.toLong()), now)
            .mapValues { it.value.totalTimeInForeground / days }
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
