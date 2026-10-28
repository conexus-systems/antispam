package br.com.conexus.antispam.decision

import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/**
 * Porte de rust/antispam-core/src/decision/mod.rs.
 * Ordem: modo OFF > emergência > contato > allowlist > blacklist > regras > 0303 > score > campanha > STIR.
 * JVM puro: sem Android APIs.
 */
object DecisionEngine {

    private const val SIGNAL_WINDOW_MS = 30L * 60L * 1000L
    private const val MIN_WEIGHTED_REPORTERS = 2.0

    val MODE_DEFAULTS: Map<ProtectionMode, DecisionEngineConfig> = mapOf(
        ProtectionMode.OFF to DecisionEngineConfig(
            mode = ProtectionMode.OFF,
            blockThreshold = 101,
            silenceThreshold = 101,
            warnThreshold = 101,
            handle0303 = "allow",
            blockUnknownInAggressive = false,
        ),
        ProtectionMode.BASIC to DecisionEngineConfig(
            mode = ProtectionMode.BASIC,
            blockThreshold = 90,
            silenceThreshold = 95,
            warnThreshold = 70,
            handle0303 = "identify",
            blockUnknownInAggressive = false,
        ),
        ProtectionMode.BALANCED to DecisionEngineConfig(
            mode = ProtectionMode.BALANCED,
            blockThreshold = 75,
            silenceThreshold = 85,
            warnThreshold = 55,
            handle0303 = "silence",
            blockUnknownInAggressive = false,
        ),
        ProtectionMode.AGGRESSIVE to DecisionEngineConfig(
            mode = ProtectionMode.AGGRESSIVE,
            blockThreshold = 60,
            silenceThreshold = 70,
            warnThreshold = 40,
            handle0303 = "block",
            blockUnknownInAggressive = true,
        ),
    )

    fun normalizePhone(raw: String?): NormalizedNumber = PhoneNormalizer.normalize(raw)

    /** Com fail-safe: qualquer erro interno resulta em ALLOW. */
    suspend fun decide(input: EngineInput): CallDecision {
        return try {
            decideUnsafe(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CallDecision(
                action = CallAction.ALLOW,
                score = 0,
                confidence = Confidence.LOW,
                reasons = listOf(DecisionReason.FAILSAFE),
                explanation = listOf(
                    "Falha interna ao analisar a chamada — permitida por segurança (fail-safe): ${e.message}"
                ),
                normalized = normalizePhone(input.ctx.rawNumber),
            )
        }
    }

    private suspend fun decideUnsafe(input: EngineInput): CallDecision {
        val config = input.config
        val ctx = input.ctx
        val normalized = PhoneNormalizer.normalize(ctx.rawNumber)
        val explanation = mutableListOf<String>()

        // 0) Modo OFF
        if (config.mode == ProtectionMode.OFF) {
            return allowDecision(
                normalized,
                listOf(DecisionReason.MODE_UNKNOWN),
                listOf("Proteção desativada — chamada permitida."),
            )
        }

        // 1) Emergência: regra absoluta
        if (normalized.kind == NumberKind.EMERGENCY) {
            return allowDecision(
                normalized,
                listOf(DecisionReason.EMERGENCY_NUMBER),
                listOf("Número de emergência — nunca é bloqueado."),
            )
        }

        // 2) Contatos e allowlist
        val contactName = ctx.contactName
        val isContact = !contactName.isNullOrEmpty() ||
            input.contactNumbers.contains(normalized.e164 ?: "")
        if (isContact) {
            return allowDecision(
                normalized,
                listOf(DecisionReason.CONTACT),
                listOf("Chamada de contato (${contactName ?: "na agenda"})."),
            )
        }
        val e164 = normalized.e164
        if (e164 != null && e164 in input.allowlist) {
            return allowDecision(
                normalized,
                listOf(DecisionReason.ALLOWLIST),
                listOf("Número na sua lista de permitidos."),
            )
        }

        // 3) Blacklist explícita
        if (e164 != null && e164 in input.blacklist) {
            return CallDecision(
                action = CallAction.BLOCK,
                score = 100,
                confidence = Confidence.HIGH,
                reasons = listOf(DecisionReason.BLACKLIST),
                explanation = listOf("Número na sua lista de bloqueados."),
                normalized = normalized,
            )
        }

        // 4) Regras do usuário (primeira que casa vence)
        val rule = input.rules.firstOrNull { it.enabled && matchesRule(it.match, normalized, isContact, ctx.presentation) }
        if (rule != null) {
            explanation.add("Regra \"${rule.name}\" aplicada.")
            val action = rule.action
            if (action == CallAction.ALLOW) {
                return allowDecision(normalized, listOf(DecisionReason.USER_RULE), explanation.toList())
            }
            return CallDecision(
                action = action,
                score = if (action == CallAction.BLOCK) 95 else 70,
                confidence = Confidence.HIGH,
                reasons = listOf(DecisionReason.USER_RULE),
                explanation = explanation.toList(),
                normalized = normalized,
            )
        }

        // 5) Tratamento específico de 0303
        if (isZeroThreeZero(normalized) && config.handle0303 !in listOf("allow", "identify")) {
            val action = if (config.handle0303 == "block") CallAction.BLOCK else CallAction.SILENCE
            return CallDecision(
                action = action,
                score = 88,
                confidence = Confidence.MEDIUM,
                reasons = listOf(DecisionReason.BRAZIL_RULE),
                explanation = listOf("Chamada 0303 (rota de telemarketing) — tratamento configurado por você."),
                normalized = normalized,
            )
        }

        // 6) Score composto
        val reputation: ReputationSignal? = if (e164 != null) {
            val entry = input.lookupReputation(e164)
            val weight = entry?.uniqueReporters?.toDouble() ?: 0.0
            if (entry == null || weight < MIN_WEIGHTED_REPORTERS) null
            else ReputationSignal(score = entry.score, weight = weight)
        } else {
            // Comportamento do Rust: Reputation::default() sem número => sinal (0, 0.0)
            ReputationSignal(score = 0, weight = 0.0)
        }

        val campaign = detectCampaign(
            CampaignSignal(number = e164 ?: "", at = ctx.at),
            input.recentCalls,
            ctx.at,
        )

        val previouslyBlocked = e164 != null && e164 in input.previouslyBlocked
        val previouslyAllowed = e164 != null && e164 in input.previouslyAllowed

        val breakdown = computeSpamScore(
            normalized,
            reputation,
            if (campaign.risk > 0) campaign else null,
            ctx.stirShaken,
            previouslyBlocked,
            previouslyAllowed,
        )

        explanation.addAll(breakdown.contributions.map { "${it.label} (+${it.points})" })

        // 7) Agressivo: internacional fora dos contatos
        val finalScore: Int
        val finalConfidence: Confidence
        if (config.blockUnknownInAggressive && !isContact && normalized.kind == NumberKind.INTERNATIONAL) {
            val newScore = maxOf(breakdown.score, config.blockThreshold)
            finalScore = newScore
            finalConfidence = if (newScore >= config.blockThreshold) Confidence.MEDIUM else breakdown.confidence
        } else {
            finalScore = breakdown.score
            finalConfidence = breakdown.confidence
        }

        // 8) Decisão final
        val action = actionForScore(finalScore, config, finalConfidence)

        val reasons = mutableListOf<DecisionReason>()
        if (action == CallAction.ALLOW && finalScore >= config.warnThreshold / 2 && breakdown.contributions.isNotEmpty()) {
            reasons.add(DecisionReason.PATTERN)
        }
        if (campaign.risk > 0) reasons.add(DecisionReason.CAMPAIGN)
        if (reputation != null) reasons.add(DecisionReason.LOCAL_REPUTATION)
        if (ctx.stirShaken == StirShaken.FAIL) reasons.add(DecisionReason.STIR_SHAKEN)
        if (action != CallAction.ALLOW && reasons.isEmpty()) reasons.add(DecisionReason.PATTERN)

        val finalExplanation = if (action == CallAction.ALLOW && finalScore == 0) {
            listOf("Nenhum sinal de risco encontrado.")
        } else {
            listOf("Score $finalScore/100 — ${riskBand(finalScore)}.") + explanation
        }

        return CallDecision(
            action = action,
            score = finalScore,
            confidence = finalConfidence,
            reasons = reasons,
            explanation = finalExplanation,
            normalized = normalized,
        )
    }

    private fun allowDecision(
        n: NormalizedNumber,
        reasons: List<DecisionReason>,
        explanation: List<String>,
    ): CallDecision = CallDecision(
        action = CallAction.ALLOW,
        score = 0,
        confidence = Confidence.HIGH,
        reasons = reasons,
        explanation = explanation,
        normalized = n,
    )

    private fun actionForScore(score: Int, config: DecisionEngineConfig, confidence: Confidence): CallAction {
        return when {
            score >= config.blockThreshold -> {
                if (confidence == Confidence.LOW && score < config.blockThreshold + 15) CallAction.SILENCE
                else CallAction.BLOCK
            }
            score >= config.silenceThreshold -> CallAction.SILENCE
            score >= config.warnThreshold -> CallAction.WARN
            else -> CallAction.ALLOW
        }
    }

    private fun isZeroThreeZero(n: NormalizedNumber): Boolean {
        val e164 = n.e164 ?: return false
        // Números normalizados perdem o zero inicial: 0303 vira 55303
        return e164.startsWith("+55303") || e164.startsWith("0303")
    }

    private fun matchesRule(
        match: RuleMatch,
        phone: NormalizedNumber,
        isContact: Boolean,
        presentation: CallPresentation,
    ): Boolean = when (match) {
        is RuleMatch.ByNumber -> phone.e164 == match.value
        is RuleMatch.ByPrefix -> phone.e164?.startsWith(match.value) == true
        is RuleMatch.ByDdd -> phone.ddd == match.value
        is RuleMatch.ByRegex -> phone.e164?.let { e ->
            runCatching { Regex(match.pattern).containsMatchIn(e) }.getOrDefault(false)
        } ?: false
        RuleMatch.Hidden -> phone.kind == NumberKind.HIDDEN || presentation == CallPresentation.RESTRICTED
        RuleMatch.International -> phone.kind == NumberKind.INTERNATIONAL
        RuleMatch.NotContact -> !isContact
        is RuleMatch.BySchedule -> matchesSchedule(match)
    }

    private fun matchesSchedule(schedule: RuleMatch.BySchedule): Boolean {
        val now = LocalDateTime.now()
        val weekday = now.dayOfWeek.value - 1 // 0=segunda ... 6=domingo
        val timeStr = now.hour.toString().padStart(2, '0') + ":" + now.minute.toString().padStart(2, '0')
        val inWeekdays = schedule.weekdays?.contains(weekday) ?: true
        val inTime = timeStr >= schedule.start && timeStr <= schedule.end
        return inWeekdays && inTime
    }

    private fun detectCampaign(current: CampaignSignal, recentCalls: List<CampaignSignal>, now: Long): CampaignDetection {
        val similar = recentCalls.count { call ->
            val diff = now - call.at
            diff >= 0 && diff <= SIGNAL_WINDOW_MS && isSimilarPrefix(current.number, call.number)
        }
        val risk = when {
            similar >= 5 -> 30
            similar >= 3 -> 15
            similar >= 2 -> 8
            else -> 0
        }
        return CampaignDetection(risk = risk, similarCount = similar)
    }

    private fun isSimilarPrefix(a: String, b: String): Boolean {
        val minLen = minOf(a.length, b.length, 8)
        return a.substring(0, minLen) == b.substring(0, minLen)
    }

    private fun computeSpamScore(
        phone: NormalizedNumber,
        reputation: ReputationSignal?,
        campaign: CampaignDetection?,
        stirShaken: StirShaken,
        previouslyBlocked: Boolean,
        previouslyAllowed: Boolean,
    ): SpamScoreBreakdown {
        val contributions = mutableListOf<ScoreContribution>()
        var score = 0

        // Reputação local
        if (reputation != null) {
            val points = (reputation.score.toDouble() * reputation.weight * 0.6).toInt()
            score += points
            contributions.add(ScoreContribution("Reputação local", points))
        }

        // Campanha
        if (campaign != null && campaign.risk > 0) {
            score += campaign.risk
            contributions.add(
                ScoreContribution(
                    "Campanha detectada (${campaign.similarCount} chamadas similares)",
                    campaign.risk,
                )
            )
        }

        // STIR/SHAKEN
        when (stirShaken) {
            StirShaken.FAIL -> {
                score += 25
                contributions.add(ScoreContribution("STIR/SHAKEN falhou", 25))
            }
            StirShaken.PASS -> {
                score -= 10
                contributions.add(ScoreContribution("STIR/SHAKEN válido", -10))
            }
            StirShaken.UNKNOWN -> Unit
        }

        // Histórico do usuário
        if (previouslyBlocked) {
            score += 30
            contributions.add(ScoreContribution("Bloqueado anteriormente por você", 30))
        }
        if (previouslyAllowed) {
            score -= 20
            contributions.add(ScoreContribution("Permitido anteriormente por você", -20))
        }

        // Padrões do número
        score += phonePatternPoints(phone, contributions)

        val confidence = when {
            contributions.any { it.points >= 30 } -> Confidence.HIGH
            contributions.any { it.points >= 15 } -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return SpamScoreBreakdown(
            score = score.coerceIn(0, 100),
            confidence = confidence,
            contributions = contributions,
        )
    }

    private fun phonePatternPoints(phone: NormalizedNumber, contributions: MutableList<ScoreContribution>): Int {
        var points = 0
        val digits = (phone.e164 ?: "").trimStart('+')

        // Sequências repetidas (9999) ou sequenciais (12345)
        if (hasRepeatedSequence(digits) || hasSequentialDigits(digits)) {
            points += 5
            contributions.add(ScoreContribution("Padrão numérico suspeito", 5))
        }

        // Código curto não emergencial
        if (phone.kind == NumberKind.SHORT_CODE) {
            points += 10
            contributions.add(ScoreContribution("Código curto não emergencial", 10))
        }

        return points
    }

    private fun hasRepeatedSequence(digits: String): Boolean {
        for (i in 0 until digits.length - 3) {
            val sub = digits.substring(i, i + 4)
            if (sub.all { it == sub[0] }) return true
        }
        return false
    }

    private fun hasSequentialDigits(digits: String): Boolean {
        for (seq in listOf("0123456789", "9876543210")) {
            for (i in 0 until seq.length - 3) {
                if (digits.contains(seq.substring(i, i + 4))) return true
            }
        }
        return false
    }

    private fun riskBand(score: Int): String = when {
        score >= 90 -> "Crítico"
        score >= 70 -> "Alto"
        score >= 50 -> "Médio"
        score >= 30 -> "Baixo"
        else -> "Mínimo"
    }

    private data class ReputationSignal(val score: Int, val weight: Double)

    private data class CampaignDetection(val risk: Int, val similarCount: Int)

    private data class ScoreContribution(val label: String, val points: Int)

    private data class SpamScoreBreakdown(
        val score: Int,
        val confidence: Confidence,
        val contributions: List<ScoreContribution>,
    )
}
