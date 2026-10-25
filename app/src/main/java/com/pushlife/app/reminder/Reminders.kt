package com.pushlife.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.blocking.BlockerService
import com.pushlife.app.filter.DnsFilterService
import java.time.LocalTime
import java.time.ZonedDateTime

// lembrete diário de treino, no horário das boas-vindas
object Reminders {
    fun schedule(context: Context, time: LocalTime) {
        val now = ZonedDateTime.now()
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        // horário aproximado, o Android pode atrasar uns minutos pra poupar bateria
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            alarmIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context) = Notifier.notify(
        context,
        Notifier.CHANNEL_REMINDERS,
        Notifier.ID_REMINDER,
        context.getString(R.string.reminder_title),
        context.getString(R.string.reminder_body),
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.show(context)
}

// celular reiniciou: reagenda o lembrete (Android apaga os alarmes) e religa o bloqueio
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AppGraph.profile.state.value.reminder?.let { Reminders.schedule(context, it) }
        DailyCheck.schedule(context)
        BlockerService.syncWithSettings(context)
        DnsFilterService.syncWithSettings(context)
    }
}
