package com.pushlife.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pushlife.app.AppGraph
import com.pushlife.app.R
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.progress.Streaks
import com.pushlife.app.core.progress.WeeklyChallenge
import com.pushlife.app.data.ProgressCenter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

// checagem das 20 h: sequência em risco, resumo no domingo e ajuste semanal da meta
object DailyCheck {
    private val TIME: LocalTime = LocalTime.of(20, 0)

    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.with(TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            PendingIntent.getBroadcast(
                context, 1, Intent(context, DailyCheckReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun run(context: Context, today: LocalDate = LocalDate.now()) {
        val stats = ProgressCenter.stats(today)
        val profile = AppGraph.profile.state.value
        if (profile.notifyStreak && Streaks.atRisk(stats.activeDays, today)) {
            Notifier.notify(
                context, Notifier.CHANNEL_ALERTS, Notifier.ID_STREAK,
                context.getString(R.string.notif_streak_title, Streaks.current(stats.activeDays, today)),
                context.getString(R.string.notif_streak_body),
            )
        }
        if (profile.notifyWeekly && today.dayOfWeek == DayOfWeek.SUNDAY) {
            val bank = AppGraph.bank.state.value
            val start = WeeklyChallenge.weekStart(today)
            val days = (0L..6L).map { start.plusDays(it) }
            Notifier.notify(
                context, Notifier.CHANNEL_ALERTS, Notifier.ID_WEEKLY,
                context.getString(R.string.notif_weekly_title),
                context.getString(
                    R.string.notif_weekly_body,
                    days.sumOf { bank.effortMinutesOn(it) },
                    days.sumOf { bank.countOn(it, Exercise.PUSHUP) },
                    days.count { (bank.effortByDay[it] ?: 0) > 0 },
                ),
            )
        }
        ProgressCenter.adjustGoalIfDue(today)?.let { goal ->
            Notifier.notify(
                context, Notifier.CHANNEL_ALERTS, Notifier.ID_GOAL,
                context.getString(R.string.notif_goal_title),
                context.getString(R.string.notif_goal_body, goal),
            )
        }
    }
}

class DailyCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = DailyCheck.run(context)
}
