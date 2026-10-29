package br.com.conexus.antispam.ads

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import br.com.conexus.antispam.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carrega e exibe o intersticial respeitando [InterstitialPolicy]. Chamar
 * [onVerificationCompleted] na thread principal ao fim de cada verificação manual (pausa natural).
 * Nunca exibe durante chamada, sem consentimento ou com um anúncio já em tela.
 */
@Singleton
class InterstitialController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ads: AdsManager,
    private val store: InterstitialStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var policy: InterstitialPolicy? = null
    private var ad: InterstitialAd? = null
    private var loading = false
    private var showing = false

    /** Pré-carrega (se permitido). Chamar quando a tela de verificação abrir. */
    fun preload() {
        val s = ads.state.value
        if (!s.canRequestAds || !s.sdkReady || ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded; loading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { ad = null; loading = false }
            },
        )
    }

    /** Registra a verificação e, se a política e o contexto permitirem, exibe o intersticial. */
    fun onVerificationCompleted(activity: Activity) {
        scope.launch {
            val p = policy ?: InterstitialPolicy(store.load()).also { policy = it }
            p.recordVerification()
            store.save(p.state)
            val current = ad
            if (current == null) { preload(); return@launch }
            if (showing || !p.canShow() || isInCall() || activity.isFinishing || activity.isDestroyed) return@launch
            showing = true
            current.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    p.markShown()
                    scope.launch { store.save(p.state) }
                }
                override fun onAdDismissedFullScreenContent() { finish() }
                override fun onAdFailedToShowFullScreenContent(error: AdError) { finish() }
                private fun finish() { showing = false; ad = null; preload() }
            }
            current.show(activity)
        }
    }

    /**
     * Só o modo de áudio: TelephonyManager.callState exige READ_PHONE_STATE (API 31+), que o app
     * nunca pede. Falha ao ler = "não em chamada" (o consentimento e a política já limitam o resto).
     */
    private fun isInCall(): Boolean = try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_RINGTONE ||
            audio.mode == AudioManager.MODE_IN_COMMUNICATION
    } catch (_: Exception) {
        false
    }
}
