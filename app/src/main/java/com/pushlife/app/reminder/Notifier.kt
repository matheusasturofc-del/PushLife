package com.pushlife.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pushlife.app.MainActivity
import com.pushlife.app.R

// canais e envio das notificações
object Notifier {
    const val CHANNEL_REMINDERS = "workout_reminders"
    const val CHANNEL_ALERTS = "alerts"

    const val ID_REMINDER = 1
    const val ID_TIME_LOW = 3
    const val ID_STREAK = 4
    const val ID_WEEKLY = 5
    const val ID_GOAL = 6

    fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            when (channel) {
                CHANNEL_REMINDERS -> NotificationChannel(channel, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                else -> NotificationChannel(channel, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_DEFAULT)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pushlife)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // sem permissão de notificação: só não aparece
        }
    }
}
