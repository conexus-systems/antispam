package br.com.conexus.antispam.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.os.Build
import android.util.Log
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallContext
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.CampaignSignal
import br.com.conexus.antispam.decision.DecisionEngine
import br.com.conexus.antispam.decision.DecisionEngineConfig
import br.com.conexus.antispam.decision.EngineInput
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.decision.ReputationEntry
import br.com.conexus.antispam.decision.StirShaken
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.database.ReputationEntry as DbReputationEntry
import br.com.conexus.antispam.database.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/**
 * CallScreeningService - Android 10+ (API 29+)
 * 
 * CONTRATO DE SEGURANÇA:
 * - NUNCA responder null nem travar: qualquer exceção → respondAllow() (fail-safe)
 * - Meta p95 < 100 ms no caminho local (cache/banco local); nuvem nunca decide.
 * - Preservar número/nome para o sistema quando permitido.
 */
@AndroidEntryPoint
class CallerIdScreeningService : CallScreeningService() {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var screeningCallback: ScreeningCallback

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Hilt injeta database e screeningCallback via ScreeningModule
    }

    override fun onScreenCall(call: Call) {
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    respondAllow(call)
                    return@launch
                }

                val details = call.details
                val number = details.handle?.schemeSpecificPart ?: ""
                val direction = details.callDirection

                // Somente chamadas recebidas são analisadas
                if (direction == Call.Details.DIRECTION_INCOMING) {
                    val presentation = when (details.callPresentation) {
                        Call.Details.PRESENTATION_ALLOWED -> CallPresentation.ALLOWED
                        Call.Details.PRESENTATION_PAYPHONE -> CallPresentation.PAYPHONE
                        Call.Details.PRESENTATION_RESTRICTED -> CallPresentation.RESTRICTED
                        else -> CallPresentation.UNKNOWN
                    }

                    val stirShaken = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && details.stirShakenVerificationStatus != null -> {
                            when (details.stirShakenVerificationStatus) {
                                Call.Details.STIR_SHAKEN_VERIFICATION_PASSED -> StirShaken.PASS
                                Call.Details.STIR_SHAKEN_VERIFICATION_FAILED -> StirShaken.FAIL
                                else -> StirShaken.UNKNOWN
                            }
                        }
                        else -> StirShaken.UNKNOWN
                    }

                    val ctx = CallContext(
                        rawNumber = number,
                        presentation = presentation,
                        contactName = null, // Resolvido localmente pelo app se necessário
                        stirShaken = stirShaken
                    )

                    // 1. Decisão local síncrona (rápida, <50ms)
                    val localDecision = runLocalDecisionPipeline(ctx)

                    // 2. Se ação não é BLOCK, responde imediatamente
                    // Se BLOCK, pode aguardar confirmação assíncrona (opcional)
                    val finalDecision = if (localDecision.action == CallAction.BLOCK) {
                        // Opcional: aguardar confirmação do motor JS/Kotlin via callback
                        // Com timeout fail-safe
                        awaitAsyncConfirmation(number, localDecision)
                    } else {
                        localDecision
                    }

                    val response = android.telecom.CallResponse.Builder()
                        .setDisallowCall(finalDecision.action == CallAction.BLOCK)
                        .setRejectCall(finalDecision.action == CallAction.BLOCK)
                        .setSkipCallLog(finalDecision.action == CallAction.BLOCK)
                        .setSkipNotification(finalDecision.action == CallAction.BLOCK)
                        .build()

                    respondToCall(call, response)

                    // Notificar app (para histórico, notificação local)
                    screeningCallback.publishDecision(number, finalDecision.action.name)
                } else {
                    respondAllow(call)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "onScreenCall fail-safe", t)
                respondAllow(call)
            }
        }
    }

    /**
     * Pipeline de decisão 100% local (Room + DecisionEngine Kotlin).
     * Deve completar em <50ms p95.
     */
    private suspend fun runLocalDecisionPipeline(ctx: CallContext): CallDecision {
        val db = database ?: return failSafeDecision(ctx)

        val settings = db.userSettingsDao().getSync() ?: return failSafeDecision(ctx)
        val config = buildConfig(settings)

        val allowlist = db.blocklistDao().getAllowed().firstOrNull()?.map { it.number } ?: emptyList()
        val blacklist = db.blocklistDao().getBlocked().firstOrNull()?.map { it.number } ?: emptyList()
        val history = db.historyDao().getRecent(50).firstOrNull() ?: emptyList()

        val recentCalls = history.map { CampaignSignal(it.number, java.time.Instant.parse(it.at).toEpochMilli()) }
        val contactNumbers = setOf<String>() // TODO: resolver contatos locais via ContactsContract
        val previouslyBlocked = blacklist.toSet()
        val previouslyAllowed = allowlist.toSet()

        val input = EngineInput(
            ctx = ctx,
            config = config,
            rules = emptyList(), // TODO: carregar regras do banco
            allowlist = allowlist,
            blacklist = blacklist,
            lookupReputation = { canonical ->
                db.reputationDao().get(canonical)?.let { toReputationEntry(it) }
            },
            recentCalls = recentCalls,
            contactNumbers = contactNumbers,
            previouslyBlocked = previouslyBlocked,
            previouslyAllowed = previouslyAllowed
        )

        return DecisionEngine.decide(input)
    }

    /**
     * Aguarda confirmação assíncrona do motor (JS/Kotlin via React Native bridge).
     * Fail-safe: timeout → ALLOW.
     */
    private suspend fun awaitAsyncConfirmation(number: String, localDecision: CallDecision): CallDecision {
        return try {
            // Timeout menor que o do sistema (2-3s)
            val action = screeningCallback.requestDecision(number, 1200L)
            val confirmedAction = CallAction.valueOf(action)
            if (confirmedAction == localDecision.action) {
                localDecision
            } else {
                // Motor discordou → usar decisão confirmada (mais segura)
                localDecision.copy(action = confirmedAction)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Confirmação assíncrona falhou para $number → decisão local", e)
            localDecision
        }
    }

    private fun buildConfig(settings: UserSettings): DecisionEngineConfig {
        val mode = ProtectionMode.valueOf(settings.mode)
        val baseConfig = DecisionEngine.MODE_DEFAULTS[mode] ?: DecisionEngine.MODE_DEFAULTS[ProtectionMode.BALANCED]!!
        return if (mode == ProtectionMode.CUSTOM) {
            baseConfig.copy(
                blockThreshold = settings.blockThreshold,
                silenceThreshold = settings.silenceThreshold,
                warnThreshold = settings.warnThreshold,
                handle0303 = settings.handle0303,
                blockUnknownInAggressive = settings.blockUnknownInAggressive
            )
        } else baseConfig
    }

    private fun toReputationEntry(entry: DbReputationEntry): ReputationEntry {
        return ReputationEntry(
            key = entry.key,
            country = entry.country,
            category = entry.category,
            score = entry.score,
            reports = entry.reports,
            uniqueReporters = entry.uniqueReporters,
            firstSeen = entry.firstSeen,
            lastSeen = entry.lastSeen,
            confidence = entry.confidence,
            falsePositives = entry.falsePositives
        )
    }

    private fun failSafeDecision(ctx: CallContext): CallDecision {
        val normalized = DecisionEngine.normalizePhone(ctx.rawNumber)
        return CallDecision(
            action = CallAction.ALLOW,
            score = 0,
            confidence = br.com.conexus.antispam.decision.Confidence.LOW,
            reasons = listOf(br.com.conexus.antispam.decision.DecisionReason.FAILSAFE),
            explanation = listOf("Falha interna ao analisar a chamada — permitida por segurança (fail-safe)."),
            normalized = normalized
        )
    }

    private fun respondAllow(call: Call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                respondToCall(
                    call,
                    android.telecom.CallResponse.Builder()
                        .setDisallowCall(false)
                        .setRejectCall(false)
                        .setSkipCallLog(false)
                        .setSkipNotification(false)
                        .build()
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "respondAllow failed", e)
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallerIdScreening"
    }
}