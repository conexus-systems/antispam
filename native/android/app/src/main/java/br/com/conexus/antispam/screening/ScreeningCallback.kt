package br.com.conexus.antispam.screening

/**
 * Callback entre CallerIdScreeningService e a camada que publica a decisão.
 */
interface ScreeningCallback {
    fun publishDecision(number: String, action: String)

    /** Aguarda a decisão do motor para o número; devolve a ação ou ALLOW no timeout/falha. */
    fun requestDecision(number: String, timeoutMs: Long = 1200L): String
}
