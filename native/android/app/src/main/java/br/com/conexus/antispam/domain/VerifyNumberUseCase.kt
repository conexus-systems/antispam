package br.com.conexus.antispam.domain

import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.DecisionEngine
import br.com.conexus.antispam.decision.NumberKind
import javax.inject.Inject
import javax.inject.Singleton

sealed class VerifyOutcome {
    data class Ready(val result: ScreeningResult) : VerifyOutcome()
    /** [message] em PT-BR, pronta para exibir ("Número inválido"). */
    data class Invalid(val message: String) : VerifyOutcome()
}

/**
 * Tela Verificar (RF-VER-01) e simulador da Home: o MESMO pipeline da chamada real, mas sem gravar
 * nada (não cria bloqueio e não aparece como chamada real). Para "Bloquear número"/"Denunciar"
 * depois do resultado use ListRepository.block / ReportRepository.report.
 */
@Singleton
class VerifyNumberUseCase @Inject constructor(
    private val pipeline: ScreeningPipeline,
) {
    suspend operator fun invoke(
        rawNumber: String,
        presentation: CallPresentation = CallPresentation.UNKNOWN,
    ): VerifyOutcome {
        val restricted = presentation == CallPresentation.RESTRICTED
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty() && !restricted) return VerifyOutcome.Invalid("Digite um número")
        if (!restricted) {
            val n = DecisionEngine.normalizePhone(trimmed)
            when (n.kind) {
                NumberKind.INVALID -> return VerifyOutcome.Invalid("Número inválido")
                NumberKind.LOCAL_NO_DDD -> return VerifyOutcome.Invalid("Inclua o DDD")
                NumberKind.HIDDEN ->
                    if (trimmed.lowercase() !in HIDDEN_WORDS) return VerifyOutcome.Invalid("Número inválido")
                else -> Unit
            }
        }
        return VerifyOutcome.Ready(
            pipeline.evaluate(ScreeningRequest(rawNumber = trimmed, presentation = presentation)),
        )
    }

    private companion object {
        val HIDDEN_WORDS = setOf("anonymous", "private", "unknown", "restricted", "privado", "desconhecido")
    }
}
