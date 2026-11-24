package com.example

import com.example.data.preferences.AnnouncementReadMode
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.domain.PaymentNotificationParser
import com.example.domain.SpeechFormatter
import com.example.service.SoundboxStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun parsesFonepayQrSmsWithSenderPhoneNumber() {
        val sms = """
            Rs. 1.00 received 
            From 9864843502 for RRN 420370162123, Re:test/FonepayQR 
            For support, toll-free number: 18102102121
        """.trimIndent()
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Fonepay",
            text = sms,
        )
        assertNotNull(parsed)
        assertEquals(1.0, parsed!!.amount, 0.001)
        assertEquals("NPR", parsed.currency)
        assertEquals("1", parsed.formattedAmount)
        assertEquals("9864843502", parsed.senderName)
        assertEquals(PaymentChannel.FONEPAY, parsed.channel)
    }

    @Test
    fun parsesCitizensBankEsewaCreditSmsWithoutCapturingCurrencyAsSender() {
        val sms = "Dear ANANTA, ###4041 is credited by NPR 3,500.00 on 2026-11-21, " +
            "Remarks: MB/esewa/LAW:1795228122408:CTZNNPK/5:Ananta Shrest. Support Center: 01-5970068"
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "CTZN-ALERT",
            text = sms,
        )
        assertNotNull(parsed)
        assertEquals(3500.0, parsed!!.amount, 0.001)
        assertEquals("NPR", parsed.currency)
        assertEquals("3,500", parsed.formattedAmount)
        assertEquals("Ananta Shrest", parsed.senderName)
        assertEquals(PaymentChannel.ESEWA, parsed.channel)
    }

    @Test
    fun parsesLaxmiSunriseBankCreditSmsWithoutSwiftOrCurrencyAsSender() {
        val sms = "Dear Customer, Your #92040001 has been credited by NPR 4,000.00 on 21/11/26. " +
            "Remarks:742291975687/LAW:1795228437118:LXBLNPKA\n-Laxmi Sunrise"
        val parsed = PaymentNotificationParser.parse(
            packageName = "com.google.android.apps.messaging",
            title = "Laxmi Sunrise",
            text = sms,
        )
        assertNotNull(parsed)
        assertEquals(4000.0, parsed!!.amount, 0.001)
        assertEquals("NPR", parsed.currency)
        assertEquals("4,000", parsed.formattedAmount)
        assertNull(parsed.senderName)
        assertEquals(PaymentChannel.BANK_ALERT, parsed.channel)
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
        assertNull(parsed.senderName)
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
    fun formatsPaymentOnlyAndPaymentWithSenderNumberCorrectly() {
        val paymentOnlySettings = SoundboxSettings(
            language = VoiceLanguage.ENGLISH,
            announceSender = false,
            announceSource = false,
        )
        assertEquals(AnnouncementReadMode.PAYMENT_ONLY, paymentOnlySettings.readMode)
        assertEquals(
            "Payment received. Rupees 1.",
            SpeechFormatter.formatAnnouncement(
                amount = 1.0,
                currency = "NPR",
                senderName = "9864843502",
                sourceLabel = "Fonepay",
                settings = paymentOnlySettings,
            ),
        )

        val paymentAndNumberSettings = SoundboxSettings(
            language = VoiceLanguage.ENGLISH,
            announceSender = true,
            announceSource = false,
        )
        assertEquals(AnnouncementReadMode.PAYMENT_AND_SENDER, paymentAndNumberSettings.readMode)
        assertEquals(
            "Payment received. Rupees 1, from 9 8 6 4 8 4 3 5 0 2.",
            SpeechFormatter.formatAnnouncement(
                amount = 1.0,
                currency = "NPR",
                senderName = "9864843502",
                sourceLabel = "Fonepay",
                settings = paymentAndNumberSettings,
            ),
        )

        val enWithSourceAndMerchant = SpeechFormatter.formatAnnouncement(
            amount = 3500.0,
            currency = "NPR",
            senderName = "Ramesh",
            sourceLabel = "eSewa",
            settings = SoundboxSettings(
                language = VoiceLanguage.ENGLISH,
                merchantName = "Himalayan Cafe",
                announceSender = true,
                announceSource = true,
            ),
        )
        assertEquals(
            "Payment received for Himalayan Cafe. Rupees 3,500 on eSewa, from Ramesh.",
            enWithSourceAndMerchant,
        )

        val ne = SpeechFormatter.formatAnnouncement(
            amount = 500.0,
            currency = "NPR",
            senderName = "रमेश",
            sourceLabel = "eSewa",
            settings = SoundboxSettings(
                language = VoiceLanguage.NEPALI,
                announceSender = true,
                announceSource = true,
            ),
        )
        assertEquals("पाँच सय रुपैयाँ, ईसेवा मा रमेश बाट प्राप्त भयो।", ne)
    }

    @Test
    fun convertsAmountsToNaturalNepaliSpokenWords() {
        assertEquals("एक", SpeechFormatter.nepaliNumberToWords(1))
        assertEquals("दुई सय पचास", SpeechFormatter.nepaliNumberToWords(250))
        assertEquals("तीन हजार पाँच सय", SpeechFormatter.nepaliNumberToWords(3500))
        assertEquals("चार हजार", SpeechFormatter.nepaliNumberToWords(4000))
        assertEquals("एक लाख", SpeechFormatter.nepaliNumberToWords(100_000))

        val neDecimal = SpeechFormatter.formatAnnouncement(
            amount = 1250.50,
            currency = "NPR",
            senderName = null,
            sourceLabel = "Fonepay",
            settings = SoundboxSettings(
                language = VoiceLanguage.NEPALI,
                announceSender = false,
                announceSource = false,
            ),
        )
        assertEquals("एक हजार दुई सय पचास रुपैयाँ, पचास पैसा, प्राप्त भयो।", neDecimal)
    }

    @Test
    fun deduplicatesDuplicateSmsAndNotificationPaymentsWithinWindow() {
        val stateHolder = SoundboxStateHolder()
        assertTrue(stateHolder.tryMarkRecentPayment(3500.0, "Ananta Shrest", nowMs = 1_000L))
        // Same payment arriving via both SMS receiver and NotificationListenerService 1.5s later
        assertFalse(stateHolder.tryMarkRecentPayment(3500.0, "ananta shrest", nowMs = 2_500L))
        // Different amount or outside 10s window is accepted
        assertTrue(stateHolder.tryMarkRecentPayment(4000.0, "Ananta Shrest", nowMs = 2_600L))
        assertTrue(stateHolder.tryMarkRecentPayment(3500.0, "Ananta Shrest", nowMs = 12_500L))
    }
}
