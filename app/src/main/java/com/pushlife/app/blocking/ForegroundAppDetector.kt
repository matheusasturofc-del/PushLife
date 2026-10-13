package com.pushlife.app.blocking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

/** Descobre qual app está na tela lendo os eventos de uso do Android (precisa de "Acesso aos dados de uso"). */
class ForegroundAppDetector(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private var lastQueryMs = System.currentTimeMillis() - 60_000
    private var current: String? = null

    /** O app da frente, ou null com a tela desligada. */
    fun current(): String? {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(lastQueryMs, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == ACTIVITY_RESUMED) current = event.packageName
        }
        // Um pouco de sobreposição para não perder eventos gravados com atraso.
        lastQueryMs = now - 2_000
        return if (power.isInteractive) current else null
    }

    private companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED (API 29); antes se chamava MOVE_TO_FOREGROUND, mesmo valor.
        const val ACTIVITY_RESUMED = 1
    }
}
