package com.example

import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {

    @Test
    fun parsesEsewaIncomingNotification() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.f1soft.esewa",
            title = "Fund Received",
            text = "You have received NPR 1,250.50 from Ramesh Adhikari (9841XXXXXX).",
        )
        assertNotNull(parsed)
        assertEquals(1250.50, parsed!!.amount, 0.001)
        assertEquals("NPR", parsed.currency)
        assertEquals("1,250.50", parsed.formattedAmount)
        assertEquals("Ramesh Adhikari", parsed.senderName)
        assertEquals(PaymentChannel.ESEWA, parsed.channel)
    }

    @Test
    fun parsesKhaltiIncomingNotification() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.khalti",
            title = "Money Received",
            text = "You received Rs. 500 from Sita Karki on Khalti.",
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.001)
        assertEquals("Sita Karki", parsed.senderName)
        assertEquals(PaymentChannel.KHALTI, parsed.channel)
    }

    @Test
    fun parsesDevanagariDigitsNotification() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.f1soft.esewa",
            title = "eSewa",
            text = "तपाईंको ईसेवामा रू ५०० प्राप्त भयो",
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.001)
        assertEquals(PaymentChannel.ESEWA, parsed.channel)
    }

    @Test
    fun parsesBankCreditSmsNotification() {
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Nabil Bank",
            text = "Dear Customer, your A/C 012***89 is credited with NPR 2,400.00 from Fonepay QR.",
        )
        assertNotNull(parsed)
        assertEquals(2400.0, parsed!!.amount, 0.001)
        assertEquals(PaymentChannel.FONEPAY, parsed.channel)
    }

    @Test
    fun ignoresOutgoingPaymentAndOtpNotifications() {
        val sent = PaymentNotificationParser.parse(
            packageName = "com.f1soft.esewa",
            title = "Payment Successful",
            text = "You paid NPR 500.00 to Nepal Electricity Authority.",
        )
        assertNull(sent)

        val otp = PaymentNotificationParser.parse(
            packageName = "com.f1soft.esewa",
            title = "Verification Code",
            text = "Your OTP is 482910 for Rs. 500 transaction. Do not share.",
        )
        assertNull(otp)
    }

    @Test
    fun formatsLocalizedSpeechCorrectly() {
        val en = SpeechFormatter.formatAnnouncement(
            amount = 500.0,
            currency = "NPR",
            senderName = "Ramesh",
            sourceLabel = "eSewa",
            settings = SoundboxSettings(language = VoiceLanguage.ENGLISH),
        )
        assertEquals("Payment received. Rupees 500 on eSewa from Ramesh.", en)

        val enWithMerchant = SpeechFormatter.formatAnnouncement(
            amount = 750.0,
            currency = "NPR",
            senderName = "Ramesh",
            sourceLabel = "eSewa",
            settings = SoundboxSettings(
                language = VoiceLanguage.ENGLISH,
                merchantName = "Himalayan Cafe",
            ),
        )
        assertEquals(
            "Payment received for Himalayan Cafe. Rupees 750 on eSewa from Ramesh.",
            enWithMerchant,
        )

        val ne = SpeechFormatter.formatAnnouncement(
            amount = 500.0,
            currency = "NPR",
            senderName = "रमेश",
            sourceLabel = "eSewa",
            settings = SoundboxSettings(language = VoiceLanguage.NEPALI),
        )
        assertEquals("ईसेवा मा रमेश बाट 500 रुपैयाँ प्राप्त भयो।", ne)
    }
}
