package br.com.conexus.antispam.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class AdsState(
    /** UMP permite requisitar anúncios. */
    val canRequestAds: Boolean = false,
    /** MobileAds.initialize concluído. */
    val sdkReady: Boolean = false,
    /** Mostrar o item "Opções de privacidade" em Ajustes. */
    val privacyOptionsRequired: Boolean = false,
)

/**
 * Ponto único de entrada de anúncios da UI. O SDK só é inicializado depois do consentimento,
 * em segundo plano, e nunca é tocado pelo serviço de triagem.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: ConsentManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initStarted = AtomicBoolean(false)
    private val _state = MutableStateFlow(AdsState())
    val state: StateFlow<AdsState> = _state.asStateFlow()

    /** Chamar uma vez por abertura, da Activity principal (onCreate). */
    fun start(activity: Activity) {
        consent.gather(activity) { publish() }
        // Consentimento guardado de sessões anteriores já permite iniciar sem esperar a rede.
        publish()
    }

    /** Abre as opções de privacidade (UMP) para o usuário rever a escolha. */
    fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity) { publish() }

    private fun publish() {
        val can = consent.canRequestAds
        _state.value = _state.value.copy(
            canRequestAds = can,
            privacyOptionsRequired = consent.privacyOptionsRequired,
        )
        if (can && initStarted.compareAndSet(false, true)) {
            scope.launch {
                MobileAds.initialize(context) {
                    _state.value = _state.value.copy(sdkReady = true)
                }
            }
        }
    }
}
