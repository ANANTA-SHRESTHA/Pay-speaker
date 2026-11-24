package com.example

import com.example.data.local.PaymentAlertEntity
import com.example.data.preferences.PaymentChannel
import com.example.data.preferences.SoundboxSettings
import com.example.data.preferences.VoiceLanguage
import com.example.data.remote.FirestoreMerchantSettings
import com.example.data.remote.FirestorePaymentAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreModelsTest {

    @Test
    fun `toCreateMap omits id and null senderName and strips nulls`() {
        val alert = PaymentAlertEntity(
            id = 42L,
            amount = 750.0,
            currency = "NPR",
            formattedAmount = "750",
            senderName = null,
            channelName = PaymentChannel.ESEWA.name,
            sourceLabel = "eSewa",
            packageName = "com.f1soft.esewa",
            rawTitle = "eSewa Received",
            rawMessage = "You received NPR 750",
            spokenText = "750 Rupees received on eSewa",
            wasSpoken = true,
            isSimulated = false,
            timestamp = 1_700_000_000_000L,
        )

        val map = FirestorePaymentAlert.toCreateMap("user_abc", alert)

        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("senderName"))
        assertEquals("user_abc", map["userId"])
        assertEquals(750.0, map["amount"])
        assertEquals(true, map["spoken"])
        assertEquals(false, map["simulated"])
        assertTrue(map.containsKey("createdAt"))
        assertTrue(map.containsKey("updatedAt"))
    }

    @Test
    fun `toEntity maps FirestorePaymentAlert safely with unknown channel fallback`() {
        val remote = FirestorePaymentAlert(
            id = "doc_999",
            userId = "user_abc",
            amount = 1200.0,
            currency = "NPR",
            formattedAmount = "1,200",
            senderName = "",
            channelName = "FUTURE_CHANNEL",
            sourceLabel = "FuturePay",
            packageName = "com.future.pay",
            rawTitle = "Paid",
            rawMessage = "Received Rs 1200",
            spokenText = "1200 Rupees",
            spoken = true,
            simulated = true,
            clientTimestamp = 1_700_000_100_000L,
        )

        val entity = remote.toEntity()

        assertEquals("doc_999", entity.cloudId)
        assertEquals(PaymentChannel.OTHER_WALLET.name, entity.channelName)
        assertNull(entity.senderName)
        assertEquals(1_700_000_100_000L, entity.timestamp)
        assertTrue(entity.wasSpoken)
        assertTrue(entity.isSimulated)
    }

    @Test
    fun `FirestoreMerchantSettings safely falls back on unknown enums and clamps rates`() {
        val remote = FirestoreMerchantSettings(
            id = "default",
            userId = "user_abc",
            merchantName = "Everest Shop",
            language = "UNKNOWN_LANG",
            enabledChannels = listOf("ESEWA", "UNKNOWN_CH"),
        )

        assertEquals(VoiceLanguage.ENGLISH, remote.safeLanguage())
        assertEquals(setOf(PaymentChannel.ESEWA), remote.safeChannels())

        val map = FirestoreMerchantSettings.toCreateMap(
            "user_abc",
            SoundboxSettings(merchantName = "A".repeat(80), speechRate = 5.0f, speechPitch = 0.1f),
        )
        assertEquals(40, (map["merchantName"] as String).length)
        assertEquals(1.6, map["speechRate"])
        assertEquals(0.7, map["speechPitch"])
    }
}
