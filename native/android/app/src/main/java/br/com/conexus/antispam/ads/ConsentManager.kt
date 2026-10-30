package br.com.conexus.antispam.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consentimento UMP. Pede atualização a cada abertura do app (chamar de uma Activity) e só então
 * informa se já é permitido pedir anúncios. Não marca o app como voltado a crianças.
 */
@Singleton
class ConsentManager @Inject constructor(@ApplicationContext context: Context) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    /** Consentimento já resolvido (ex.: guardado de uma sessão anterior). */
    val canRequestAds: Boolean get() = info.canRequestAds()

    /** Há uma opção de privacidade que o usuário deve poder reabrir (ex.: em Ajustes). */
    val privacyOptionsRequired: Boolean
        get() = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Atualiza o consentimento e exibe o formulário se necessário. [onDone] recebe `canRequestAds`
     * (mesmo em erro: usa o que o SDK já tem salvo).
     */
    fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onDone(info.canRequestAds())
                }
            },
            { onDone(info.canRequestAds()) },
        )
    }

    /** Reabre o formulário de opções de privacidade (botão em Ajustes). */
    fun showPrivacyOptions(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDone(info.canRequestAds()) }
    }
}
