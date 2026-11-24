package com.example.domain

import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

object SpeechFormatter {

    fun formatAnnouncement(
        amount: Double,
        currency: String,
        senderName: String?,
        sourceLabel: String,
        settings: SoundboxSettings,
    ): String {
        val merchant = settings.merchantName.trim().takeIf { it.isNotEmpty() }
        val cleanSender = senderName?.trim()?.takeIf { it.isNotEmpty() }

        return when (settings.language) {
            VoiceLanguage.ENGLISH -> {
                val amountSpeech = formatGroupedEnglishNumber(amount)
                val currencyWord = currencySpeechWord(currency, VoiceLanguage.ENGLISH)
                val spokenSender = cleanSender?.let { formatSenderForSpeech(it, devanagariDigits = false) }

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
                val senderPart = if (settings.announceSender && spokenSender != null) {
                    ", from $spokenSender"
                } else {
                    ""
                }
                "$lead$sourcePart$senderPart."
            }

            VoiceLanguage.NEPALI -> {
                val amountPhrase = formatNepaliCurrencyPhrase(amount, currency)
                val spokenSender = cleanSender?.let { formatSenderForSpeech(it, devanagariDigits = true) }

                val merchantPart = if (merchant != null) "$merchant को लागि, " else ""
                val contextParts = buildList {
                    if (settings.announceSource && sourceLabel.isNotBlank()) {
                        add("${nepaliSourceName(sourceLabel)} मा")
                    }
                    if (settings.announceSender && spokenSender != null) {
                        add("$spokenSender बाट")
                    }
                }
                val contextSegment = if (contextParts.isNotEmpty()) {
                    " ${contextParts.joinToString(" ")}"
                } else {
                    ""
                }
                "$merchantPart$amountPhrase,$contextSegment प्राप्त भयो।"
            }

            VoiceLanguage.HINDI -> {
                val amountSpeech = formatGroupedEnglishNumber(amount)
                val currencyWord = currencySpeechWord(currency, VoiceLanguage.HINDI)
                val spokenSender = cleanSender?.let { formatSenderForSpeech(it, devanagariDigits = false) }

                val merchantPart = if (merchant != null) "$merchant के लिए, " else ""
                val contextParts = buildList {
                    if (settings.announceSource && sourceLabel.isNotBlank()) {
                        add("$sourceLabel पर")
                    }
                    if (settings.announceSender && spokenSender != null) {
                        add("$spokenSender से")
                    }
                }
                val contextSegment = if (contextParts.isNotEmpty()) {
                    " ${contextParts.joinToString(" ")}"
                } else {
                    ""
                }
                "$merchantPart$amountSpeech $currencyWord,$contextSegment प्राप्त हुए।"
            }
        }
    }

    fun sampleAnnouncement(settings: SoundboxSettings): String =
        formatAnnouncement(
            amount = 500.0,
            currency = "NPR",
            senderName = "9864843502",
            sourceLabel = "Fonepay",
            settings = settings,
        )

    private fun formatSenderForSpeech(sender: String, devanagariDigits: Boolean): String {
        val withoutPlus = sender.removePrefix("+")
        return if (withoutPlus.length >= 6 && withoutPlus.all { it.isDigit() }) {
            val digits = if (devanagariDigits) toDevanagariDigits(withoutPlus) else withoutPlus
            if (devanagariDigits && digits.length == 10) {
                // Group 10-digit Nepali phone numbers naturally (4-4-2) for smooth TTS rhythm
                val a = digits.substring(0, 4).toCharArray().joinToString(" ")
                val b = digits.substring(4, 8).toCharArray().joinToString(" ")
                val c = digits.substring(8, 10).toCharArray().joinToString(" ")
                "$a, $b, $c"
            } else {
                digits.toCharArray().joinToString(" ")
            }
        } else {
            sender
        }
    }

    private fun formatGroupedEnglishNumber(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val isWhole = amount % 1.0 == 0.0
        val pattern = if (isWhole) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, symbols).format(amount)
    }

    private fun formatNepaliCurrencyPhrase(amount: Double, currency: String): String {
        val currencyWord = currencySpeechWord(currency, VoiceLanguage.NEPALI)
        val totalPaisa = (amount * 100.0).roundToLong().coerceAtLeast(0L)
        val rupees = totalPaisa / 100L
        val paisa = (totalPaisa % 100L).toInt()

        val rupeeSpeech = if (rupees <= 999_999_999L) {
            nepaliNumberToWords(rupees)
        } else {
            toDevanagariDigits(rupees.toString())
        }

        return if (paisa == 0) {
            "$rupeeSpeech $currencyWord"
        } else {
            val subUnit = if (currency.uppercase(Locale.ROOT) == "USD") "सेन्ट" else "पैसा"
            val paisaSpeech = nepaliNumberToWords(paisa.toLong())
            "$rupeeSpeech $currencyWord, $paisaSpeech $subUnit"
        }
    }

    internal fun nepaliNumberToWords(value: Long): String {
        if (value == 0L) return NEPALI_0_TO_99[0]
        if (value < 0L) return toDevanagariDigits(value.toString())

        val parts = mutableListOf<String>()
        var remaining = value

        val crore = (remaining / 10_000_000L).toInt()
        if (crore > 0) {
            parts.add("${nepaliBelowHundredOrCompound(crore)} करोड")
            remaining %= 10_000_000L
        }

        val lakh = (remaining / 100_000L).toInt()
        if (lakh > 0) {
            parts.add("${nepaliBelowHundredOrCompound(lakh)} लाख")
            remaining %= 100_000L
        }

        val thousand = (remaining / 1_000L).toInt()
        if (thousand > 0) {
            parts.add("${nepaliBelowHundredOrCompound(thousand)} हजार")
            remaining %= 1_000L
        }

        val hundred = (remaining / 100L).toInt()
        if (hundred > 0) {
            parts.add("${NEPALI_0_TO_99[hundred]} सय")
            remaining %= 100L
        }

        if (remaining > 0L) {
            parts.add(NEPALI_0_TO_99[remaining.toInt()])
        }

        return parts.joinToString(" ")
    }

    private fun nepaliBelowHundredOrCompound(n: Int): String {
        return if (n < 100) NEPALI_0_TO_99[n] else nepaliNumberToWords(n.toLong())
    }

    private fun toDevanagariDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(('०'.code + (ch.code - '0'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
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
            "connectips" -> "कनेक्ट आईपीएस"
            else -> sourceLabel
        }
    }

    private val NEPALI_0_TO_99 = arrayOf(
        "शून्य", "एक", "दुई", "तीन", "चार", "पाँच", "छ", "सात", "आठ", "नौ",
        "दस", "एघार", "बाह्र", "तेह्र", "चौध", "पन्ध्र", "सोह्र", "सत्र", "अठार", "उन्नाइस",
        "बीस", "एक्काइस", "बाइस", "तेइस", "चौबिस", "पच्चिस", "छब्बिस", "सत्ताइस", "अठ्ठाइस", "उनन्तीस",
        "तीस", "एकतीस", "बत्तीस", "तेत्तीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अठतीस", "उनन्चालीस",
        "चालीस", "एकचालीस", "बयालीस", "त्रिचालीस", "चौवालीस", "पैंतालीस", "छयालीस", "सतचालीस", "अठचालीस", "उनन्चास",
        "पचास", "एकाउन्न", "बाउन्न", "त्रिपन्न", "चौवन्न", "पचपन्न", "छपन्न", "सन्ताउन्न", "अन्ठाउन्न", "उनन्साठी",
        "साठी", "एकसट्ठी", "बइसट्ठी", "त्रिसट्ठी", "चौंसट्ठी", "पैंसट्ठी", "छैसट्ठी", "सतसट्ठी", "अठसट्ठी", "उनन्सत्तरी",
        "सत्तरी", "एकहत्तर", "बहत्तर", "त्रिहत्तर", "चौहत्तर", "पचहत्तर", "छयहत्तर", "सत्तहत्तर", "अठहत्तर", "उनासी",
        "असी", "एकासी", "बयासी", "त्रियासी", "चौरासी", "पचासी", "छयासी", "सतासी", "अठासी", "उनान्नब्बे",
        "नब्बे", "एकानब्बे", "बयानब्बे", "त्रियानब्बे", "चौरानब्बे", "पन्चानब्बे", "छयानब्बे", "सन्तानब्बे", "अन्ठानब्बे", "उनान्सय",
    )
}
