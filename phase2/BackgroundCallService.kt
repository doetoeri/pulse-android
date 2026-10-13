package com.pulse.assistant

import android.app.*
import android.content.*
import android.os.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat

/** Keeps an accepted PULSE session alive independently of the Activity. */
object CallRuntime {
    var active by mutableStateOf(false)
        internal set
    var startedAt by mutableStateOf(0L)
        internal set
    var status by mutableStateOf("통화 준비")
        internal set
}

class BackgroundCallService : Service() {
    companion object {
        const val START = "com.pulse.assistant.START_CALL"
        const val STOP = "com.pulse.assistant.STOP_CALL"
        const val LISTEN = "com.pulse.assistant.LISTEN"
        const val CHANNEL = "pulse_ongoing_call"
        const val NOTIFICATION_ID = 4201
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private var voice: VoiceSession? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "PULSE 통화", NotificationManager.IMPORTANCE_LOW).apply {
                description = "진행 중인 PULSE 음성 통화"
                setSound(null, null)
            }
        )
        voice = VoiceSession(this) { message ->
            CallRuntime.status = message
            if (CallRuntime.active) notifyCall()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> {
                stopCall()
                return START_NOT_STICKY
            }
            LISTEN -> { if (CallRuntime.active) voice?.listen() }
            START -> {
                if (!CallRuntime.active) {
                    CallRuntime.active = true
                    CallRuntime.startedAt = System.currentTimeMillis()
                    CallRuntime.status = "통화 중 · 화면을 꺼도 유지됩니다"
                    val power = getSystemService(POWER_SERVICE) as PowerManager
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PULSE:OngoingCall").apply {
                        acquire(60 * 60 * 1000L)
                    }
                    startForeground(NOTIFICATION_ID, notification())
                    voice?.greet()
                }
            }
        }
        return START_NOT_STICKY
    }

    fun listen() = voice?.listen()

    private fun notification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val open = PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val end = PendingIntent.getService(
            this, 1, Intent(this, BackgroundCallService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("PULSE 통화 중")
            .setContentText(CallRuntime.status.take(80))
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "통화 종료", end)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun notifyCall() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification())
    }

    private fun stopCall() {
        CallRuntime.active = false
        CallRuntime.status = "통화 종료"
        voice?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        CallRuntime.active = false
        voice?.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
