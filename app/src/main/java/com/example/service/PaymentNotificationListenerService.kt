package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.PaySpeakerApp
import com.example.data.local.PaymentAlertEntity
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as? PaySpeakerApp)?.container?.stateHolder?.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        (application as? PaySpeakerApp)?.container?.stateHolder?.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val fullBody = listOf(text, subText).filter { it.isNotBlank() }.joinToString(" ")
        val parsed = PaymentNotificationParser.parse(
            packageName = pkg,
            title = title,
            text = fullBody,
        ) ?: return

        val container = (application as? PaySpeakerApp)?.container ?: return
        if (!container.stateHolder.tryMarkRecentPayment(parsed.amount, parsed.senderName)) {
            return
        }

        val hasCachedSettings = container.preferencesRepository.hasLoadedFromDisk
        val cachedSettings = container.preferencesRepository.latestSettings
        var spokenImmediately: String? = null

        if (hasCachedSettings) {
            if (parsed.channel !in cachedSettings.enabledChannels) {
                return
            }
            spokenImmediately = if (cachedSettings.isSpeakerEnabled) {
                container.speakerEngine.announcePayment(
                    amount = parsed.amount,
                    currency = parsed.currency,
                    senderName = parsed.senderName,
                    sourceLabel = parsed.sourceLabel,
                    settings = cachedSettings,
                )
            } else {
                SpeechFormatter.formatAnnouncement(
                    amount = parsed.amount,
                    currency = parsed.currency,
                    senderName = parsed.senderName,
                    sourceLabel = parsed.sourceLabel,
                    settings = cachedSettings,
                )
            }
        }

        serviceScope.launch {
            val settings = if (hasCachedSettings) cachedSettings else container.preferencesRepository.settingsFlow.first()
            if (!hasCachedSettings && parsed.channel !in settings.enabledChannels) {
                return@launch
            }

            val spokenText = spokenImmediately ?: if (settings.isSpeakerEnabled) {
                container.speakerEngine.announcePayment(
                    amount = parsed.amount,
                    currency = parsed.currency,
                    senderName = parsed.senderName,
                    sourceLabel = parsed.sourceLabel,
                    settings = settings,
                )
            } else {
                SpeechFormatter.formatAnnouncement(
                    amount = parsed.amount,
                    currency = parsed.currency,
                    senderName = parsed.senderName,
                    sourceLabel = parsed.sourceLabel,
                    settings = settings,
                )
            }

            val entity = PaymentAlertEntity(
                amount = parsed.amount,
                currency = parsed.currency,
                formattedAmount = parsed.formattedAmount,
                senderName = parsed.senderName,
                channelName = parsed.channel.name,
                sourceLabel = parsed.sourceLabel,
                packageName = parsed.packageName,
                rawTitle = parsed.rawTitle,
                rawMessage = parsed.rawText,
                spokenText = spokenText,
                wasSpoken = settings.isSpeakerEnabled,
                isSimulated = false,
            )
            container.paymentRepository.recordAlert(entity)
            container.firestorePaymentRepository.addAlertIfSignedIn(entity)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
