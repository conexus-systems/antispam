package br.com.conexus.antispam.screening

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.NumberKind
import br.com.conexus.antispam.domain.ScreeningResult

/**
 * Como responder ao sistema (CallScreeningService.CallResponse). Puro, testável na JVM.
 *
 * - BLOCK: disallow + reject (conforme ajuste) e sem notificação de chamada perdida.
 *   skipCallLog = false de propósito: a chamada continua no registro do sistema (o usuário pode auditar no
 *   discador); o histórico do app é gravado de qualquer forma.
 * - SILENCE: setSilenceCall(true), a chamada entra sem tocar.
 * - WARN/ALLOW: segue normal (WARN só gera notificação opcional).
 */
data class ResponsePlan(
    val disallowCall: Boolean,
    val rejectCall: Boolean,
    val silenceCall: Boolean,
    val skipCallLog: Boolean,
    val skipNotification: Boolean,
) {
    companion object {
        val ALLOW = ResponsePlan(false, false, false, false, false)

        fun from(result: ScreeningResult): ResponsePlan {
            // Defesa em profundidade: emergência nunca é bloqueada nem silenciada, mesmo que o motor mude.
            if (result.decision.normalized.kind == NumberKind.EMERGENCY) return ALLOW
            return when (result.decision.action) {
                CallAction.BLOCK -> ResponsePlan(
                    disallowCall = true,
                    rejectCall = result.policy.rejectBlockedCalls,
                    silenceCall = false,
                    skipCallLog = false,
                    skipNotification = true,
                )
                CallAction.SILENCE -> ALLOW.copy(silenceCall = true)
                CallAction.WARN, CallAction.ALLOW -> ALLOW
            }
        }
    }
}
