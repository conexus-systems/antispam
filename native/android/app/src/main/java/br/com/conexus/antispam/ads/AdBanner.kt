package br.com.conexus.antispam.ads

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.conexus.antispam.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Banner adaptativo ancorado. Enquanto o consentimento não permite anúncios, ou se o carregamento
 * falhar, ocupa 0 de altura (some). Quando pode pedir, reserva desde o início a altura exata do
 * banner adaptativo para o conteúdo não pular quando o anúncio chegar.
 */
@SuppressLint("MissingPermission")
@Composable
fun AdBanner(adsManager: AdsManager, modifier: Modifier = Modifier) {
    val state by adsManager.state.collectAsState()
    if (!state.canRequestAds || !state.sdkReady) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    var failed by remember { mutableStateOf(false) }
    if (failed) return

    val adView = remember(adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }

    Box(modifier.fillMaxWidth().height(adSize.height.dp)) {
        AndroidView(factory = { adView })
    }
}
