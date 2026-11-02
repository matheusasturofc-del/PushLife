package com.pushlife.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.core.packs.PackCatalog
import com.pushlife.app.core.packs.PackPlanner
import com.pushlife.app.core.packs.Workouts
import com.pushlife.app.ui.nameRes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

// às 9 h: "Hoje tem Braço e peito (~15 min)" nos dias que a pessoa escolheu pra cada programa
object PackReminder {
    private val TIME: LocalTime = LocalTime.of(9, 0)

    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.with(TIME).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            PendingIntent.getBroadcast(
                context, 2, Intent(context, PackReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun run(context: Context, today: LocalDate = LocalDate.now()) {
        if (!AppGraph.packs.notifyToday) return
        val due = PackPlanner.dueToday(AppGraph.packs.state.value, today)
        if (due.isEmpty()) return
        val first = PackCatalog.get(due.first())
        val minutes = Workouts.minutes(Workouts.build(first, AppGraph.packs.progress(first.id).level))
        val name = context.getString(first.id.nameRes)
        val text = if (due.size == 1) {
            context.getString(R.string.notif_pack_body, name, minutes)
        } else {
            context.getString(R.string.notif_pack_body_many, due.joinToString(" · ") { context.getString(it.nameRes) })
        }
        Notifier.notify(context, Notifier.CHANNEL_REMINDERS, Notifier.ID_PACKS, context.getString(R.string.notif_pack_title), text)
    }
}

class PackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PackReminder.run(context)
}
