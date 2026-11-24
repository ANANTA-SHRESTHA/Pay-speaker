package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private enum class BannerLoadState {
    LOADING,
    LOADED,
    FAILED,
}

@Composable
fun AdMobBannerBar(
    adUnitId: String,
    isSdkInitialized: Boolean,
    retryTrigger: Int = 0,
    onBannerLoading: () -> Unit = {},
    onBannerLoaded: () -> Unit = {},
    onBannerFailed: (LoadAdError) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!isSdkInitialized || adUnitId.isBlank()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loadState by remember(adUnitId, retryTrigger) { mutableStateOf(BannerLoadState.LOADING) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_container"),
        contentAlignment = Alignment.Center,
    ) {
        val adWidthDp = maxWidth.value.toInt().coerceIn(320, 728)
        val adaptiveSize = remember(adWidthDp, context) {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        }

        val adView = remember(adUnitId, adaptiveSize) {
            AdView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                this.adUnitId = adUnitId
                setAdSize(adaptiveSize)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        loadState = BannerLoadState.LOADED
                        onBannerLoaded()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadState = BannerLoadState.FAILED
                        onBannerFailed(error)
                    }
                }
            }
        }

        LaunchedEffect(adView, retryTrigger) {
            loadState = BannerLoadState.LOADING
            onBannerLoading()
            adView.post {
                adView.loadAd(AdRequest.Builder().build())
            }
        }

        DisposableEffect(adView, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }

        if (loadState != BannerLoadState.FAILED) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AndroidView(
                        factory = { adView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = adaptiveSize.height.dp)
                            .padding(vertical = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
