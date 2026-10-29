package br.com.conexus.antispam.screening

import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.StirShaken
import br.com.conexus.antispam.domain.ScreeningPipeline
import br.com.conexus.antispam.domain.ScreeningRequest
import br.com.conexus.antispam.domain.ScreeningResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * CallScreeningService (API 29+). Só chamadas recebidas.
 *
 * Contrato:
 * - Decisão = ScreeningPipeline (motor local + Room). Nenhuma rede neste caminho.
 * - O Android dá ~5 s: decisão limitada a [DECISION_TIMEOUT_MS] e gravação a [RECORD_TIMEOUT_MS];
 *   estouro ou exceção = ALLOW (fail-safe).
 * - Histórico é gravado ANTES de responder, em toda decisão (inclusive ALLOW).
 * - Sem POST_NOTIFICATIONS não notifica, mas bloqueia do mesmo jeito.
 * - Emergência nunca é bloqueada (motor + [ResponsePlan]).
 */
@AndroidEntryPoint
class CallerIdScreeningService : CallScreeningService() {

    @Inject lateinit var pipeline: ScreeningPipeline
    @Inject lateinit var notifier: ScreeningNotifier

    // Sem cancelamento em onDestroy: o trabalho é limitado por timeouts e o sistema precisa da resposta.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            details.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respond(details, ResponsePlan.ALLOW)
            return
        }
        scope.launch {
            var plan = ResponsePlan.ALLOW
            try {
                val request = ScreeningRequest(
                    rawNumber = details.handle?.schemeSpecificPart.orEmpty(),
                    presentation = presentationOf(details),
                    stirShaken = stirShakenOf(details),
                )
                val result = decideWithTimeout(request)
                persistAndNotify(result)
                plan = ResponsePlan.from(result)
            } catch (t: Throwable) {
                Log.e(TAG, "onScreenCall fail-safe", t)
                plan = ResponsePlan.ALLOW
            } finally {
                respond(details, plan)
            }
        }
    }

    private suspend fun decideWithTimeout(request: ScreeningRequest): ScreeningResult = try {
        withTimeout(DECISION_TIMEOUT_MS) { pipeline.evaluate(request) }
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Decisão excedeu ${DECISION_TIMEOUT_MS}ms: ALLOW (fail-safe)")
        pipeline.failSafe(request, "tempo esgotado")
    }

    /** Grava o histórico (única evidência quando o sistema oculta a chamada) e notifica. Nunca lança. */
    private suspend fun persistAndNotify(result: ScreeningResult) {
        try {
            withContext(NonCancellable) { withTimeout(RECORD_TIMEOUT_MS) { pipeline.record(result) } }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao gravar histórico", t)
        }
        try {
            notifier.notify(result)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao notificar", t)
        }
    }

    private fun respond(details: Call.Details, plan: ResponsePlan) {
        try {
            val builder = CallScreeningService.CallResponse.Builder()
                .setDisallowCall(plan.disallowCall)
                .setRejectCall(plan.rejectCall)
                .setSilenceCall(plan.silenceCall)
                .setSkipCallLog(plan.skipCallLog)
                .setSkipNotification(plan.skipNotification)
            respondToCall(details, builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "respondToCall falhou", t)
        }
    }

    private fun presentationOf(details: Call.Details): CallPresentation = when (details.handlePresentation) {
        CallLog.Calls.PRESENTATION_ALLOWED -> CallPresentation.ALLOWED
        CallLog.Calls.PRESENTATION_PAYPHONE -> CallPresentation.PAYPHONE
        CallLog.Calls.PRESENTATION_RESTRICTED -> CallPresentation.RESTRICTED
        else -> CallPresentation.UNKNOWN
    }

    // callerNumberVerificationStatus existe a partir da API 30 (minSdk do app é 29).
    private fun stirShakenOf(details: Call.Details): StirShaken =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (details.callerNumberVerificationStatus) {
                Connection.VERIFICATION_STATUS_PASSED -> StirShaken.PASS
                Connection.VERIFICATION_STATUS_FAILED -> StirShaken.FAIL
                else -> StirShaken.UNKNOWN
            }
        } else StirShaken.UNKNOWN

    companion object {
        private const val TAG = "CallerIdScreening"
        /** Bem abaixo dos ~5 s do sistema. */
        const val DECISION_TIMEOUT_MS = 2500L
        const val RECORD_TIMEOUT_MS = 1000L
    }
}
