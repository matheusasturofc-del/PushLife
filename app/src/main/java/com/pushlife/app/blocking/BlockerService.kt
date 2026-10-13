package com.pushlife.app.blocking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pushlife.app.AppGraph
import com.pushlife.app.MainActivity
import com.pushlife.app.R
import com.pushlife.app.core.BlockerEngine
import com.pushlife.app.reminder.Notifier
import com.pushlife.app.ui.formatDuration
import java.time.LocalDate

/**
 * Serviço em primeiro plano (com notificação fixa) que, a cada segundo, vê qual app está aberto,
 * desconta o tempo dos apps bloqueados e abre a tela de bloqueio quando o saldo acaba.
 */
class BlockerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val engine = BlockerEngine()
    private lateinit var detector: ForegroundAppDetector
    private var warnedLow = false

    private val tick = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        detector = ForegroundAppDetector(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        flush()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onTick() {
        val settings = AppGraph.blocking.state.value
        if (!settings.enabled || !Permissions.hasUsageAccess(this)) {
            stopSelf()
            return
        }
        val bank = AppGraph.bank.state.value
        val foreground = detector.current()
        val events = engine.tick(
            foreground = foreground,
            blocked = settings.blockedPackages,
            nowMs = System.currentTimeMillis(),
            availableSeconds = bank.available(LocalDate.now()),
            emergencyUntilMs = bank.emergencyUntilMs,
        )
        for (event in events) {
            when (event) {
                is BlockerEngine.Event.Entered -> Toast.makeText(
                    this,
                    getString(R.string.entered_toast, this.formatDuration(event.remainingSeconds)),
                    Toast.LENGTH_SHORT,
                ).show()
                is BlockerEngine.Event.Block -> {
                    flush()
                    BlockActivity.open(this, event.packageName)
                }
                is BlockerEngine.Event.Left -> flush()
            }
        }
        if (engine.pendingSeconds >= FLUSH_EVERY_SECONDS) flush()
        warnIfLow(foreground != null && foreground in settings.blockedPackages, bank.available(LocalDate.now()) - engine.pendingSeconds)
    }

    /** Avisa uma vez quando o saldo fica abaixo de 5 min usando um app bloqueado. */
    private fun warnIfLow(inBlockedApp: Boolean, remaining: Long) {
        if (remaining > LOW_SECONDS) {
            warnedLow = false
            return
        }
        if (!inBlockedApp || warnedLow || remaining <= 0 || !AppGraph.profile.state.value.notifyLowTime) return
        warnedLow = true
        Notifier.notify(
            this, Notifier.CHANNEL_ALERTS, Notifier.ID_TIME_LOW,
            getString(R.string.notif_low_title, this.formatDuration(remaining.coerceAtLeast(60))),
            getString(R.string.notif_low_body),
        )
    }

    private fun flush() {
        val seconds = engine.takePending()
        if (seconds > 0) AppGraph.bank.spend(seconds)
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pushlife)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 2
        private const val TICK_MS = 1_000L
        private const val FLUSH_EVERY_SECONDS = 15
        private const val LOW_SECONDS = 5 * 60L

        /** Liga o serviço se a proteção estiver ativa e as permissões liberadas. */
        fun syncWithSettings(context: Context) {
            val settings = AppGraph.blocking.state.value
            val intent = Intent(context, BlockerService::class.java)
            if (settings.enabled && settings.blockedPackages.isNotEmpty() && Permissions.allRequired(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
