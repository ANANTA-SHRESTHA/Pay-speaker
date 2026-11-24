package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestorePaymentAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val currency: String = "NPR",
    val formattedAmount: String = "0",
    val senderName: String? = null,
    val channelName: String = PaymentChannel.OTHER_WALLET.name,
    val sourceLabel: String = "Payment",
    val packageName: String = "",
    val rawTitle: String = "",
    val rawMessage: String = "",
    val spokenText: String = "",
    val spoken: Boolean = true,
    val simulated: Boolean = false,
    val clientTimestamp: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toEntity(): PaymentAlertEntity {
        val safeChannel = PaymentChannel.entries
            .firstOrNull { it.name == channelName }
            ?.name
            ?: PaymentChannel.OTHER_WALLET.name

        val resolvedTimestamp = clientTimestamp.takeIf { it > 0L }
            ?: createdAt?.toDate()?.time
            ?: System.currentTimeMillis()

        val syntheticId = id.hashCode().toLong() and 0x7FFFFFFFL

        return PaymentAlertEntity(
            id = syntheticId,
            amount = amount,
            currency = currency,
            formattedAmount = formattedAmount,
            senderName = senderName?.takeIf { it.isNotBlank() },
            channelName = safeChannel,
            sourceLabel = sourceLabel,
            packageName = packageName,
            rawTitle = rawTitle,
            rawMessage = rawMessage,
            spokenText = spokenText,
            wasSpoken = spoken,
            isSimulated = simulated,
            timestamp = resolvedTimestamp,
            cloudId = id,
        )
    }

    companion object {
        private val VALID_CHANNELS = PaymentChannel.entries.map { it.name }.toSet()

        fun toCreateMap(
            uid: String,
            alert: PaymentAlertEntity,
        ): Map<String, Any> {
            val safeChannel = if (alert.channelName in VALID_CHANNELS) {
                alert.channelName
            } else {
                PaymentChannel.OTHER_WALLET.name
            }
            val safeAmount = alert.amount.coerceIn(0.01, 1_000_000_000.0)
            val safeTimestamp = alert.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
            val cleanSender = alert.senderName?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            @Suppress("UNCHECKED_CAST")
            return mapOf(
                "userId" to uid.take(128),
                "amount" to safeAmount,
                "currency" to alert.currency.ifBlank { "NPR" }.take(16),
                "formattedAmount" to alert.formattedAmount.ifBlank { "0" }.take(32),
                "senderName" to cleanSender,
                "channelName" to safeChannel,
                "sourceLabel" to alert.sourceLabel.ifBlank { "Payment" }.take(60),
                "packageName" to alert.packageName.ifBlank { "com.payspeaker.app" }.take(200),
                "rawTitle" to alert.rawTitle.take(300),
                "rawMessage" to alert.rawMessage.take(1000),
                "spokenText" to alert.spokenText.take(1000),
                "spoken" to alert.wasSpoken,
                "simulated" to alert.isSimulated,
                "clientTimestamp" to safeTimestamp,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
        }
    }
}

@Keep
@IgnoreExtraProperties
data class FirestoreMerchantSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val merchantName: String = "",
    val language: String = VoiceLanguage.ENGLISH.name,
    val speechRate: Double = 1.0,
    val speechPitch: Double = 1.0,
    val boostVolume: Boolean = true,
    val playChime: Boolean = true,
    val announceSender: Boolean = true,
    val announceSource: Boolean = false,
    val repeatTwice: Boolean = false,
    val enabledChannels: List<String> = PaymentChannel.entries.map { it.name },
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun safeLanguage(): VoiceLanguage =
        VoiceLanguage.entries.firstOrNull { it.name == language } ?: VoiceLanguage.ENGLISH

    fun safeChannels(): Set<PaymentChannel> {
        val mapped = enabledChannels.mapNotNull { name ->
            PaymentChannel.entries.firstOrNull { it.name == name }
        }.toSet()
        return mapped.ifEmpty { PaymentChannel.entries.toSet() }
    }

    companion object {
        fun toMutableFieldsMap(settings: SoundboxSettings): Map<String, Any> {
            return mapOf(
                "merchantName" to settings.merchantName.take(40),
                "language" to settings.language.name,
                "speechRate" to settings.speechRate.toDouble().coerceIn(0.6, 1.6),
                "speechPitch" to settings.speechPitch.toDouble().coerceIn(0.7, 1.4),
                "boostVolume" to settings.boostVolume,
                "playChime" to settings.playChime,
                "announceSender" to settings.announceSender,
                "announceSource" to settings.announceSource,
                "repeatTwice" to settings.repeatTwice,
                "enabledChannels" to settings.enabledChannels.map { it.name }.take(10),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        }

        fun toCreateMap(uid: String, settings: SoundboxSettings): Map<String, Any> {
            return toMutableFieldsMap(settings) + mapOf(
                "userId" to uid.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        }
    }
}
