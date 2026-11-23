package com.example.domain

import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import java.util.Locale

object SpeechFormatter {

    fun formatAnnouncement(
        amount: Double,
        currency: String,
        senderName: String?,
        sourceLabel: String,
        settings: SoundboxSettings,
    ): String {
        val amountSpeech = formatSpokenNumber(amount)
        val currencyWord = currencySpeechWord(currency, settings.language)
        val merchant = settings.merchantName.trim().takeIf { it.isNotEmpty() }

        return when (settings.language) {
            VoiceLanguage.ENGLISH -> {
                val lead = if (merchant != null) {
                    "Payment received for $merchant. $currencyWord $amountSpeech"
                } else {
                    "Payment received. $currencyWord $amountSpeech"
                }
                val sourcePart = if (settings.announceSource && sourceLabel.isNotBlank()) {
                    " on $sourceLabel"
                } else {
                    ""
                }
                val senderPart = if (settings.announceSender && !senderName.isNullOrBlank()) {
                    " from $senderName"
                } else {
                    ""
                }
                "$lead$sourcePart$senderPart."
            }

            VoiceLanguage.NEPALI -> {
                val merchantPart = if (merchant != null) "$merchant को लागि " else ""
                val sourcePart = if (settings.announceSource && sourceLabel.isNotBlank()) {
                    "${nepaliSourceName(sourceLabel)} मा "
                } else {
                    ""
                }
                val senderPart = if (settings.announceSender && !senderName.isNullOrBlank()) {
                    "$senderName बाट "
                } else {
                    ""
                }
                "$merchantPart$sourcePart$senderPart$amountSpeech $currencyWord प्राप्त भयो।"
            }

            VoiceLanguage.HINDI -> {
                val merchantPart = if (merchant != null) "$merchant के लिए " else ""
                val sourcePart = if (settings.announceSource && sourceLabel.isNotBlank()) {
                    "$sourceLabel पर "
                } else {
                    ""
                }
                val senderPart = if (settings.announceSender && !senderName.isNullOrBlank()) {
                    "$senderName से "
                } else {
                    ""
                }
                "$merchantPart$sourcePart$senderPart$amountSpeech $currencyWord प्राप्त हुए।"
            }
        }
    }

    fun sampleAnnouncement(settings: SoundboxSettings): String =
        formatAnnouncement(
            amount = 500.0,
            currency = "NPR",
            senderName = if (settings.language == VoiceLanguage.NEPALI) "रमेश अधिकारी" else "Ramesh",
            sourceLabel = "eSewa",
            settings = settings,
        )

    private fun formatSpokenNumber(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }

    private fun currencySpeechWord(currency: String, language: VoiceLanguage): String {
        return when (language) {
            VoiceLanguage.ENGLISH -> when (currency.uppercase(Locale.ROOT)) {
                "INR" -> "Rupees"
                "USD" -> "Dollars"
                else -> "Rupees"
            }
            VoiceLanguage.NEPALI -> when (currency.uppercase(Locale.ROOT)) {
                "USD" -> "डलर"
                else -> "रुपैयाँ"
            }
            VoiceLanguage.HINDI -> when (currency.uppercase(Locale.ROOT)) {
                "USD" -> "डॉलर"
                else -> "रुपये"
            }
        }
    }

    private fun nepaliSourceName(sourceLabel: String): String {
        return when (sourceLabel.lowercase(Locale.ROOT)) {
            "esewa" -> "ईसेवा"
            "khalti" -> "खल्ती"
            "fonepay" -> "फोनपे"
            "ime pay" -> "आईएमई पे"
            else -> sourceLabel
        }
    }
}
