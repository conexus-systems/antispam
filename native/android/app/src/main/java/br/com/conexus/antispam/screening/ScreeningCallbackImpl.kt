package br.com.conexus.antispam.screening

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Implementação do callback entre CallerIdScreeningService (nativo)
 * e AntiSpamScreeningModule (React Native bridge).
 * 
 * Usa CompletableFuture por número para aguardar decisão do motor Kotlin/JS
 * com timeout (fail-safe: ALLOW se não responder a tempo).
 */
class ScreeningCallbackImpl : ScreeningCallback {

    private val pendingDecisions = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val DECISION_TIMEOUT_MS = 1200L // Nunca aproximar do timeout do sistema (2-3s)

    override fun publishDecision(number: String, action: String) {
        val future = pendingDecisions.remove(number)
        future?.complete(action)
    }

    /**
     * Solicita decisão do motor (JS/Kotlin via React Native) para um número.
     * Retorna ação (ALLOW/BLOCK/WARN/SILENCE) ou ALLOW no timeout/falha.
     */
    fun requestDecision(number: String, timeoutMs: Long = DECISION_TIMEOUT_MS): String {
        val future = CompletableFuture<String>()
        val prev = pendingDecisions.putIfAbsent(number, future)
        if (prev != null) {
            // Já havia decisão pendente para este número (race raro)
            prev.complete("ALLOW")
        }

        // Notifica JS/Kotlin via React Native bridge para processar
        // O módulo nativo deve chamar notifyDecision quando pronto
        notifyJsForDecision(number)

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            Log.w(TAG, "Timeout aguardando decisão para $number → ALLOW (fail-safe)")
            "ALLOW"
        } catch (e: Exception) {
            Log.e(TAG, "Erro aguardando decisão para $number", e)
            "ALLOW"
        } finally {
            pendingDecisions.remove(number)
        }
    }

    private fun notifyJsForDecision(number: String) {
        // Emite evento para o lado JS (React Native) processar
        // O módulo React Native deve ouvir "onScreenCall" e chamar notifyDecision
        // Isso é feito via DeviceEventManagerModule.RCTDeviceEventEmitter
        scope.launch {
            try {
                // TODO: Implementar emissão de evento para React Native
                // reactContext
                //     .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                //     .emit("onScreenCall", mapOf("number" to number))
            } catch (e: Exception) {
                Log.e(TAG, "Erro emitindo evento onScreenCall", e)
            }
        }
    }

    companion object {
        private const val TAG = "ScreeningCallback"
    }
}