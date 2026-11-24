package com.example

import com.example.ads.AdMobManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMobManagerTest {

    @Test
    fun `detects official Google sample AdMob IDs vs live PaySpeaker IDs`() {
        assertTrue(AdMobManager.isSampleAdMobId("ca-app-pub-3940256099942544~3347511713"))
        assertTrue(AdMobManager.isSampleAdMobId("ca-app-pub-3940256099942544/9214589741"))
        assertFalse(AdMobManager.isSampleAdMobId("ca-app-pub-3835499116565370~6158630414"))
        assertFalse(AdMobManager.isSampleAdMobId("ca-app-pub-3835499116565370/4923324384"))
    }

    @Test
    fun `interstitial cooldown blocks rapid consecutive impressions and active TTS speech`() {
        val now = 1_000_000L

        assertFalse(
            AdMobManager.canShowInterstitial(
                hasLoadedAd = false,
                isSpeaking = false,
                lastShownAtMillis = 0L,
                nowMillis = now,
            )
        )
        assertFalse(
            AdMobManager.canShowInterstitial(
                hasLoadedAd = true,
                isSpeaking = true,
                lastShownAtMillis = 0L,
                nowMillis = now,
            )
        )
        assertTrue(
            AdMobManager.canShowInterstitial(
                hasLoadedAd = true,
                isSpeaking = false,
                lastShownAtMillis = 0L,
                nowMillis = now,
            )
        )
        assertFalse(
            AdMobManager.canShowInterstitial(
                hasLoadedAd = true,
                isSpeaking = false,
                lastShownAtMillis = now - 60_000L,
                nowMillis = now,
            )
        )
        assertTrue(
            AdMobManager.canShowInterstitial(
                hasLoadedAd = true,
                isSpeaking = false,
                lastShownAtMillis = now - 180_000L,
                nowMillis = now,
            )
        )
    }
}
