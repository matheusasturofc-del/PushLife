package com.pushlife.app.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

// descobre o app na tela pelos eventos de uso (precisa do "Acesso aos dados de uso")
class ForegroundAppDetector(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private var lastQueryMs = System.currentTimeMillis() - 60_000
    private var current: String? = null

    // app da frente, ou null com a tela desligada
    fun current(): String? {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(lastQueryMs, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == ACTIVITY_RESUMED) current = event.packageName
        }
        // volta 2 s pra não perder evento atrasado
        lastQueryMs = now - 2_000
        return if (power.isInteractive) current else null
    }

    private companion object {
        // ACTIVITY_RESUMED (API 29), o antigo MOVE_TO_FOREGROUND, mesmo valor
        const val ACTIVITY_RESUMED = 1
    }
}
