package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.BlocklistDao
import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.SystemRules
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.database.UserSettingsDao
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallContext
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.CampaignSignal
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.DecisionEngine
import br.com.conexus.antispam.decision.DecisionEngineConfig
import br.com.conexus.antispam.decision.DecisionReason
import br.com.conexus.antispam.decision.EngineInput
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.database.ReputationEntry as DbReputationEntry
import br.com.conexus.antispam.decision.ReputationEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * O pipeline ÚNICO de decisão: monta o EngineInput real (listas, regras, reputação, configurações,
 * histórico) e chama DecisionEngine.decide. Usado pelo serviço de triagem, pela tela Verificar e
 * pelo simulador da Home. Só lê o banco local: nunca usa rede.
 *
 * - [evaluate]: sem efeitos colaterais (Verificar/Home).
 * - [record]: grava o resultado no histórico (serviço).
 * - [failSafe]: resultado ALLOW por segurança (exceção/timeout).
 */
@Singleton
class ScreeningPipeline @Inject constructor(
    private val settingsDao: UserSettingsDao,
    private val blocklistDao: BlocklistDao,
    private val ruleDao: RuleDao,
    private val reputationDao: ReputationDao,
    private val historyDao: HistoryDao,
    private val history: HistoryRepository,
    private val clock: Clock,
) {

    /** Decide. Qualquer erro vira ALLOW (fail-safe); cancelamento/timeout propaga. */
    suspend fun evaluate(request: ScreeningRequest): ScreeningResult {
        return try {
            evaluateUnsafe(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failSafe(request, e.message)
        }
    }

    /** Grava no histórico (simulated = false). Devolve o item gravado. */
    suspend fun record(result: ScreeningResult, simulated: Boolean = false): HistoryItem =
        history.record(result.decision, result.atMillis, result.request.contactName, simulated)

    /** Resultado ALLOW com razão FAILSAFE. [detail] entra na explicação. */
    fun failSafe(request: ScreeningRequest, detail: String? = null): ScreeningResult {
        val normalized = DecisionEngine.normalizePhone(request.rawNumber)
        val text = "Falha interna ao analisar a chamada: permitida por segurança" +
            (detail?.let { " ($it)" } ?: "") + "."
        val decision = CallDecision(
            action = CallAction.ALLOW,
            score = 0,
            confidence = Confidence.LOW,
            reasons = listOf(DecisionReason.FAILSAFE),
            explanation = listOf(text),
            normalized = normalized,
        )
        return toResult(request, decision, ListStatus.NONE, ResponsePolicy(), request.atMillis ?: clock.nowMillis())
    }

    private suspend fun evaluateUnsafe(request: ScreeningRequest): ScreeningResult {
        val now = request.atMillis ?: clock.nowMillis()
        val settings = settingsDao.getSync() ?: UserSettings()
        val ruleEntities = ruleDao.getAll()
        val rules = ruleEntities.mapNotNull { RulesRepository.toEngineRule(it) }
        val has0303Rule = ruleEntities.any { it.id == SystemRules.ZERO_THREE_ZERO_ID }
        val allowlist = blocklistDao.numbers(ListRepository.ALLOW)
        val blacklist = blocklistDao.numbers(ListRepository.BLOCK)
        val recent = historyDao.getRecentReal(RECENT_WINDOW)

        val ctx = CallContext(
            rawNumber = request.rawNumber,
            contactName = request.contactName,
            presentation = request.presentation,
            stirShaken = request.stirShaken,
            at = now,
        )
        val input = EngineInput(
            config = engineConfig(settings, has0303Rule),
            ctx = ctx,
            contactNumbers = emptySet(), // MVP: sem READ_CONTACTS (spec §8)
            allowlist = allowlist,
            blacklist = blacklist,
            rules = rules,
            recentCalls = recent.map { CampaignSignal(it.number, IsoTime.parse(it.at)) },
            previouslyBlocked = recent.filter { it.action == CallAction.BLOCK.name }.map { it.number }.toSet(),
            previouslyAllowed = emptySet(),
            lookupReputation = { key -> reputationDao.get(key)?.let(::toEngineReputation) },
        )
        val decision = DecisionEngine.decide(input)
        val e164 = decision.normalized.e164
        val listStatus = when {
            e164 == null -> ListStatus.NONE
            e164 in blacklist -> ListStatus.BLOCKED
            e164 in allowlist -> ListStatus.ALLOWED
            else -> ListStatus.NONE
        }
        val policy = ResponsePolicy(settings.rejectBlockedCalls, settings.notificationsEnabled)
        return toResult(request, decision, listStatus, policy, now)
    }

    private fun toResult(
        request: ScreeningRequest,
        decision: CallDecision,
        listStatus: ListStatus,
        policy: ResponsePolicy,
        atMillis: Long,
    ) = ScreeningResult(
        request = request,
        decision = decision,
        number = PhoneFormat.key(decision.normalized),
        displayNumber = PhoneFormat.display(decision.normalized),
        actionLabel = decision.action.label(),
        headline = headlineFor(decision.action, decision.reasons),
        riskLabel = riskBandLabel(decision.score),
        explanation = decision.explanation,
        listStatus = listStatus,
        policy = policy,
        atMillis = atMillis,
    )

    companion object {
        const val RECENT_WINDOW = 50

        /**
         * Configuração do motor a partir dos ajustes. O tratamento de 0303 vem da regra de sistema
         * (que respeita prioridade e é editável); por isso, com a regra presente, o passo 0303 interno
         * do motor fica em "allow" para não duplicar nem contradizer a regra.
         */
        fun engineConfig(settings: UserSettings, has0303Rule: Boolean): DecisionEngineConfig {
            val mode = runCatching { ProtectionMode.valueOf(settings.mode) }.getOrDefault(ProtectionMode.BALANCED)
            val base = DecisionEngine.MODE_DEFAULTS[mode] ?: DecisionEngine.MODE_DEFAULTS.getValue(ProtectionMode.BALANCED)
            val config = if (mode == ProtectionMode.CUSTOM) {
                base.copy(
                    mode = mode,
                    blockThreshold = settings.blockThreshold,
                    silenceThreshold = settings.silenceThreshold,
                    warnThreshold = settings.warnThreshold,
                    handle0303 = settings.handle0303,
                    blockUnknownInAggressive = settings.blockUnknownInAggressive,
                )
            } else base
            return if (has0303Rule && mode != ProtectionMode.OFF) config.copy(handle0303 = "allow") else config
        }

        fun toEngineReputation(e: DbReputationEntry) = ReputationEntry(
            key = e.key,
            country = e.country,
            category = e.category,
            score = e.score,
            reports = e.reports,
            uniqueReporters = e.uniqueReporters,
            firstSeen = e.firstSeen,
            lastSeen = e.lastSeen,
            confidence = e.confidence,
            falsePositives = e.falsePositives,
        )
    }
}
