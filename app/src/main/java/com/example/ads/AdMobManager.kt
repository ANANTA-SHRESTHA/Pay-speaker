package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AdMobManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initStarted = AtomicBoolean(false)
    private val isLoadingInterstitial = AtomicBoolean(false)

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var lastInterstitialShownAtMillis: Long = 0L

    private val _isSdkInitialized = MutableStateFlow(false)
    val isSdkInitialized: StateFlow<Boolean> = _isSdkInitialized.asStateFlow()

    private val _interstitialReady = MutableStateFlow(false)
    val interstitialReady: StateFlow<Boolean> = _interstitialReady.asStateFlow()

    private val _bannerStatusMessage = MutableStateFlow("Initializing Google Mobile Ads…")
    val bannerStatusMessage: StateFlow<String> = _bannerStatusMessage.asStateFlow()

    private val _bannerRetryCount = MutableStateFlow(0)
    val bannerRetryCount: StateFlow<Int> = _bannerRetryCount.asStateFlow()

    val appId: String get() = BuildConfig.ADMOB_APP_ID
    val bannerAdUnitId: String get() = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    val interstitialAdUnitId: String get() = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    val isUsingTestIds: Boolean
        get() = isSampleAdMobId(appId) || isSampleAdMobId(bannerAdUnitId)

    fun initialize(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            // Unblock banner loading after at most 2 seconds even if mediation adapter init stalls
            launch {
                delay(2_000L)
                if (!_isSdkInitialized.value) {
                    _isSdkInitialized.value = true
                    _bannerStatusMessage.value = "Requesting banner ad…"
                }
            }
            runCatching {
                MobileAds.initialize(appContext) {
                    _isSdkInitialized.value = true
                    if (_bannerStatusMessage.value.startsWith("Initializing")) {
                        _bannerStatusMessage.value = "Requesting banner ad…"
                    }
                    scope.launch(Dispatchers.Main) {
                        preloadInterstitial(appContext)
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "MobileAds initialization skipped/failed: ${err.message}")
                _bannerStatusMessage.value = "AdMob init error: ${err.message ?: "Unknown"}"
            }
        }
    }

    fun reportBannerLoading() {
        _bannerStatusMessage.value = "Requesting banner ad…"
    }

    fun reportBannerLoaded() {
        _bannerStatusMessage.value = "Banner ad loaded and active"
    }

    fun reportBannerFailed(error: LoadAdError) {
        _bannerStatusMessage.value = formatLoadError(error)
        Log.w(TAG, "Banner failed to load: ${error.code} - ${error.message}")
    }

    fun retryBannerAd() {
        _bannerStatusMessage.value = "Retrying banner ad…"
        _bannerRetryCount.value = _bannerRetryCount.value + 1
    }

    fun preloadInterstitial(context: Context) {
        val unitId = interstitialAdUnitId.trim()
        if (unitId.isEmpty() || interstitialAd != null || !isLoadingInterstitial.compareAndSet(false, true)) {
            return
        }
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Main) {
            runCatching {
                InterstitialAd.load(
                    appContext,
                    unitId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            isLoadingInterstitial.set(false)
                            interstitialAd = ad
                            _interstitialReady.value = true
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isLoadingInterstitial.set(false)
                            interstitialAd = null
                            _interstitialReady.value = false
                            Log.d(TAG, "Interstitial failed to load: ${error.message}")
                        }
                    },
                )
            }.onFailure {
                isLoadingInterstitial.set(false)
            }
        }
    }

    fun maybeShowInterstitial(
        activity: Activity?,
        isSpeaking: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (interstitialAdUnitId.isBlank()) return false
        val host = activity ?: return false
        val ad = interstitialAd
        if (!canShowInterstitial(
                hasLoadedAd = ad != null,
                isSpeaking = isSpeaking,
                lastShownAtMillis = lastInterstitialShownAtMillis,
                nowMillis = nowMillis,
            )
        ) {
            if (ad == null) {
                preloadInterstitial(host)
            }
            return false
        }

        val readyAd = ad ?: return false
        lastInterstitialShownAtMillis = nowMillis
        interstitialAd = null
        _interstitialReady.value = false

        readyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadInterstitial(host)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                preloadInterstitial(host)
            }
        }
        readyAd.show(host)
        return true
    }

    companion object {
        private const val TAG = "AdMobManager"
        private const val GOOGLE_SAMPLE_PUB_PREFIX = "ca-app-pub-3940256099942544"
        const val MIN_INTERSTITIAL_INTERVAL_MS = 180_000L

        fun isSampleAdMobId(id: String): Boolean =
            id.trim().startsWith(GOOGLE_SAMPLE_PUB_PREFIX)

        fun formatLoadError(error: LoadAdError): String {
            return when (error.code) {
                AdRequest.ERROR_CODE_NO_FILL ->
                    "No ad fill yet (code 3). New AdMob units often take 1-24 hours to start serving."
                AdRequest.ERROR_CODE_NETWORK_ERROR ->
                    "Network error (code 2). Check internet connection."
                AdRequest.ERROR_CODE_INVALID_REQUEST ->
                    "Invalid ad request (code 1): ${error.message}"
                AdRequest.ERROR_CODE_INTERNAL_ERROR ->
                    "AdMob internal error (code 0): ${error.message}"
                else ->
                    "Ad unavailable (code ${error.code}): ${error.message}"
            }
        }

        fun canShowInterstitial(
            hasLoadedAd: Boolean,
            isSpeaking: Boolean,
            lastShownAtMillis: Long,
            nowMillis: Long,
            minIntervalMillis: Long = MIN_INTERSTITIAL_INTERVAL_MS,
        ): Boolean {
            if (!hasLoadedAd || isSpeaking) return false
            if (lastShownAtMillis <= 0L) return true
            return (nowMillis - lastShownAtMillis) >= minIntervalMillis
        }
    }
}
