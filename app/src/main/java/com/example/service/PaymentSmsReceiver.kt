package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.PaySpeakerApp
import com.example.data.local.PaymentAlertEntity
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaymentSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val groupedBySender = LinkedHashMap<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress
                ?: sms.originatingAddress
                ?: "Bank SMS"
            val body = sms.displayMessageBody ?: sms.messageBody ?: ""
            if (body.isNotBlank()) {
                groupedBySender.getOrPut(sender) { StringBuilder() }.append(body)
            }
        }
        if (groupedBySender.isEmpty()) return

        val app = context.applicationContext as? PaySpeakerApp ?: return
        val container = app.container
        val hasCachedSettings = container.preferencesRepository.hasLoadedFromDisk
        val cachedSettings = container.preferencesRepository.latestSettings

        val parsedItems = groupedBySender.mapNotNull { (senderAddress, bodyBuilder) ->
            val fullBody = bodyBuilder.toString().trim()
            val parsed = PaymentNotificationParser.parse(
                packageName = SMS_SOURCE_PACKAGE,
                title = senderAddress,
                text = fullBody,
            ) ?: return@mapNotNull null

            if (hasCachedSettings && parsed.channel !in cachedSettings.enabledChannels) {
                return@mapNotNull null
            }
            if (!container.stateHolder.tryMarkRecentPayment(parsed.amount, parsed.senderName)) {
                return@mapNotNull null
            }

            val spokenNow = if (hasCachedSettings) {
                if (cachedSettings.isSpeakerEnabled) {
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
            } else {
                null
            }
            parsed to spokenNow
        }

        if (parsedItems.isEmpty()) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = if (hasCachedSettings) {
                    cachedSettings
                } else {
                    container.preferencesRepository.settingsFlow.first()
                }
                for ((parsed, alreadySpoken) in parsedItems) {
                    if (!hasCachedSettings && parsed.channel !in settings.enabledChannels) {
                        continue
                    }

                    val spokenText = alreadySpoken ?: if (settings.isSpeakerEnabled) {
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
            } catch (e: Exception) {
                Log.w(TAG, "Error handling incoming SMS: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PaymentSmsReceiver"
        private const val SMS_SOURCE_PACKAGE = "com.android.mms"
    }
}
