package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PaySpeakerApp
import com.example.R
import com.example.domain.SpeechFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SoundboxForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as? PaySpeakerApp)?.container
        when (intent?.action) {
            ACTION_TOGGLE_MUTE -> {
                scope.launch {
                    container?.let { c ->
                        val current = c.preferencesRepository.settingsFlow.first()
                        val next = !current.isSpeakerEnabled
                        c.preferencesRepository.setSpeakerEnabled(next)
                        updateNotification(next)
                    }
                }
            }
            ACTION_TEST_SOUND -> {
                scope.launch {
                    container?.let { c ->
                        val settings = c.preferencesRepository.settingsFlow.first()
                        val englishFallback = SpeechFormatter.sampleAnnouncement(
                            settings.copy(language = com.example.data.preferences.VoiceLanguage.ENGLISH)
                        )
                        val text = SpeechFormatter.sampleAnnouncement(settings)
                        c.speakerEngine.announceText(text, englishFallback, settings)
                    }
                }
            }
            ACTION_STOP -> {
                container?.stateHolder?.setForegroundGuardRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                scope.launch {
                    val enabled = container?.preferencesRepository?.settingsFlow?.first()?.isSpeakerEnabled ?: true
                    startForeground(NOTIFICATION_ID, buildNotification(enabled))
                    container?.stateHolder?.setForegroundGuardRunning(true)
                }
            }
        }
        return START_STICKY
    }

    private fun updateNotification(speakerEnabled: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(speakerEnabled))
    }

    private fun buildNotification(speakerEnabled: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val testIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SoundboxForegroundService::class.java).setAction(ACTION_TEST_SOUND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, SoundboxForegroundService::class.java).setAction(ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = getString(
            if (speakerEnabled) R.string.fg_notif_title else R.string.fg_notif_muted_title
        )
        val text = getString(
            if (speakerEnabled) R.string.fg_notif_text else R.string.fg_notif_muted_text
        )
        val toggleLabel = getString(
            if (speakerEnabled) R.string.fg_action_mute else R.string.fg_action_resume
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.fg_action_test), testIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.fg_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as? PaySpeakerApp)?.container?.stateHolder?.setForegroundGuardRunning(false)
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "payspeaker_soundbox_guard"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_SOUNDBOX"
        const val ACTION_STOP = "com.example.action.STOP_SOUNDBOX"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
        const val ACTION_TEST_SOUND = "com.example.action.TEST_SOUND"

        fun start(context: Context) {
            val intent = Intent(context, SoundboxForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SoundboxForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
