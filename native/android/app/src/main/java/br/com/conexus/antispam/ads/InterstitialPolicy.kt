package br.com.conexus.antispam.ads

/** Estado persistível da política de intersticial. */
data class InterstitialState(
    /** Verificações concluídas desde o último intersticial exibido. */
    val actionsSinceAd: Int = 0,
    val lastShownAt: Long = 0L,
    val hourWindowStart: Long = 0L,
    val hourCount: Int = 0,
)

/**
 * Política de frequência do intersticial (JVM puro, relógio injetado). Porta de src/ads/frequency.ts:
 * 1 a cada [ACTIONS_BEFORE_INTERSTITIAL] verificações, intervalo mínimo de [COOLDOWN_MS]
 * e no máximo [MAX_PER_HOUR] por hora.
 */
class InterstitialPolicy(
    initial: InterstitialState = InterstitialState(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var state: InterstitialState = initial
        private set

    /** Registra uma verificação concluída. */
    fun recordVerification() {
        state = state.copy(actionsSinceAd = state.actionsSinceAd + 1)
    }

    /** Pode exibir agora? Não altera o estado. */
    fun canShow(): Boolean {
        val now = clock()
        if (state.actionsSinceAd < ACTIONS_BEFORE_INTERSTITIAL) return false
        if (state.lastShownAt > 0 && now - state.lastShownAt < COOLDOWN_MS) return false
        val windowExpired = now - state.hourWindowStart > HOUR_MS
        val count = if (windowExpired) 0 else state.hourCount
        return count < MAX_PER_HOUR
    }

    /** Chamar quando o intersticial foi efetivamente exibido. */
    fun markShown() {
        val now = clock()
        val expired = now - state.hourWindowStart > HOUR_MS
        state = InterstitialState(
            actionsSinceAd = 0,
            lastShownAt = now,
            hourWindowStart = if (expired) now else state.hourWindowStart,
            hourCount = (if (expired) 0 else state.hourCount) + 1,
        )
    }

    companion object {
        const val ACTIONS_BEFORE_INTERSTITIAL = 3
        const val COOLDOWN_MS = 3 * 60_000L
        const val MAX_PER_HOUR = 3
        private const val HOUR_MS = 3_600_000L
    }
}
