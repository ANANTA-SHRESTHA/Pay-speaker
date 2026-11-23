package com.example.domain

import com.example.data.preferences.PaymentChannel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class ParsedPaymentAlert(
    val amount: Double,
    val currency: String,
    val formattedAmount: String,
    val senderName: String?,
    val channel: PaymentChannel,
    val sourceLabel: String,
    val rawTitle: String,
    val rawText: String,
    val packageName: String,
)

object PaymentNotificationParser {

    private val promoOrOtpTerms = listOf(
        "otp",
        "one time password",
        "verification code",
        "security code",
        "cashback offer",
        "win up to",
        "spin and win",
        "promo code",
        "limited offer",
        "discount",
        "expires in",
        "login alert",
    )

    private val outgoingTerms = listOf(
        "sent to",
        "paid to",
        "transferred to",
        "debited",
        "withdrawn",
        "purchase of",
        "topup",
        "top-up",
        "recharge",
        "bill payment",
        "you paid",
        "you sent",
        "पठाइएको",
        "खर्च",
    )

    private val incomingCreditTerms = listOf(
        "received",
        "credited",
        "deposited",
        "fund received",
        "money received",
        "payment received",
        "added to your",
        "loaded to your",
        "received rs",
        "received npr",
        "received inr",
        "प्राप्त",
        "जम्मा",
    )

    private val leadingCurrencyAmountRegex = Regex(
        """(?i)(?:NPR|NRs\.?|Rs\.?|रू\.?|INR|₹|USD|\$)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    private val trailingCurrencyAmountRegex = Regex(
        """(?i)([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:NPR|NRs\.?|Rs\.?|रू\.?|INR|₹|rupees|रुपैयाँ|रुपये)\b"""
    )

    private val senderRegex = Regex(
        """(?i)\b(?:from|by)\s+([A-Za-z0-9\u0900-\u097F][A-Za-z0-9\u0900-\u097F .'*_-]{1,36}?)(?=\s*(?:\(|on\b|for\b|at\b|via\b|remarks\b|ref\b|\.|,|!|$))"""
    )

    fun parse(
        packageName: String,
        title: String,
        text: String,
    ): ParsedPaymentAlert? {
        val rawCombined = listOf(title, text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" - ")

        if (rawCombined.isBlank()) return null

        val normalized = normalizeDevanagariDigits(rawCombined)
        val lower = normalized.lowercase(Locale.ROOT)

        if (promoOrOtpTerms.any { lower.contains(it) }) {
            return null
        }

        val hasIncomingTerm = incomingCreditTerms.any { lower.contains(it) }
        val hasOutgoingTerm = outgoingTerms.any { lower.contains(it) }

        if (!hasIncomingTerm) {
            return null
        }
        if (hasOutgoingTerm && !lower.contains("credited") && !lower.contains("received from")) {
            return null
        }

        val amountMatch = extractAmountAndCurrency(normalized) ?: return null
        val (amount, currency) = amountMatch
        if (amount <= 0.0) return null

        val channel = detectChannel(packageName, lower)
        val sender = extractSender(normalized)

        return ParsedPaymentAlert(
            amount = amount,
            currency = currency,
            formattedAmount = formatAmount(amount),
            senderName = sender,
            channel = channel,
            sourceLabel = channel.displayName,
            rawTitle = title.trim(),
            rawText = text.trim(),
            packageName = packageName,
        )
    }

    fun formatAmount(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val isWhole = amount % 1.0 == 0.0
        val pattern = if (isWhole) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, symbols).format(amount)
    }

    private fun normalizeDevanagariDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '०'..'९') {
                sb.append(('0'.code + (ch.code - '०'.code)).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun extractAmountAndCurrency(text: String): Pair<Double, String>? {
        val leadingMatch = leadingCurrencyAmountRegex.find(text)
        if (leadingMatch != null) {
            val numberStr = leadingMatch.groupValues[1].replace(",", "")
            val amount = numberStr.toDoubleOrNull()
            if (amount != null) {
                val currency = detectCurrencyToken(leadingMatch.value)
                return amount to currency
            }
        }

        val trailingMatch = trailingCurrencyAmountRegex.find(text)
        if (trailingMatch != null) {
            val numberStr = trailingMatch.groupValues[1].replace(",", "")
            val amount = numberStr.toDoubleOrNull()
            if (amount != null) {
                val currency = detectCurrencyToken(trailingMatch.value)
                return amount to currency
            }
        }

        return null
    }

    private fun detectCurrencyToken(matchedSnippet: String): String {
        val upper = matchedSnippet.uppercase(Locale.ROOT)
        return when {
            upper.contains("INR") || matchedSnippet.contains("₹") || matchedSnippet.contains("रुपये") -> "INR"
            upper.contains("USD") || matchedSnippet.contains("$") -> "USD"
            else -> "NPR"
        }
    }

    private fun detectChannel(packageName: String, lowerText: String): PaymentChannel {
        val pkgLower = packageName.lowercase(Locale.ROOT)
        PaymentChannel.entries.forEach { channel ->
            if (channel != PaymentChannel.BANK_ALERT && channel.packageHints.any { pkgLower.contains(it) }) {
                return channel
            }
        }
        PaymentChannel.entries.forEach { channel ->
            if (channel.keywordHints.any { lowerText.contains(it) }) {
                return channel
            }
        }
        if (PaymentChannel.BANK_ALERT.packageHints.any { pkgLower.contains(it) }) {
            return PaymentChannel.BANK_ALERT
        }
        return PaymentChannel.OTHER_WALLET
    }

    private fun extractSender(text: String): String? {
        val match = senderRegex.find(text) ?: return null
        val candidate = match.groupValues[1]
            .trim()
            .trimEnd('.', ',', '-', ':')
            .replace(Regex("""\s+"""), " ")

        val lowerCandidate = candidate.lowercase(Locale.ROOT)
        val invalidSenders = setOf(
            "esewa",
            "khalti",
            "fonepay",
            "ime pay",
            "connectips",
            "your account",
            "bank",
            "qr",
        )
        if (candidate.length < 2 || lowerCandidate in invalidSenders) {
            return null
        }
        return candidate
    }
}
