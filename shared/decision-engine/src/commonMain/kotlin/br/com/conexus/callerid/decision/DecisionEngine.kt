package br.com.conexus.callerid.decision

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Decision Engine - Core spam detection logic (Kotlin Multiplatform)
 * Ported from TypeScript implementation with same behavior guarantees.
 */
object DecisionEngine {

    private val MODE_DEFAULTS: Map<ProtectionMode, DecisionEngineConfig> = mapOf(
        ProtectionMode.OFF to DecisionEngineConfig(
            mode = ProtectionMode.OFF,
            blockThreshold = 101, silenceThreshold = 101, warnThreshold = 101,
            handle0303 = "allow", blockUnknownInAggressive = false
        ),
        ProtectionMode.BASIC to DecisionEngineConfig(
            mode = ProtectionMode.BASIC,
            blockThreshold = 90, silenceThreshold = 95, warnThreshold = 70,
            handle0303 = "identify", blockUnknownInAggressive = false
        ),
        ProtectionMode.BALANCED to DecisionEngineConfig(
            mode = ProtectionMode.BALANCED,
            blockThreshold = 75, silenceThreshold = 85, warnThreshold = 55,
            handle0303 = "silence", blockUnknownInAggressive = false
        ),
        ProtectionMode.AGGRESSIVE to DecisionEngineConfig(
            mode = ProtectionMode.AGGRESSIVE,
            blockThreshold = 60, silenceThreshold = 70, warnThreshold = 40,
            handle0303 = "block", blockUnknownInAggressive = true
        )
    )

    fun decide(input: EngineInput): CallDecision {
        return try {
            decideUnsafe(input)
        } catch (e: Exception) {
            // FAIL-SAFE: erro interno → ALLOW
            val normalized = normalizePhone(input.ctx.rawNumber)
            CallDecision(
                action = CallAction.ALLOW,
                score = 0,
                confidence = Confidence.LOW,
                reasons = listOf(DecisionReason.FAILSAFE),
                explanation = listOf("Falha interna ao analisar a chamada — permitida por segurança (fail-safe)."),
                normalized = normalized
            )
        }
    }

    private fun decideUnsafe(input: EngineInput): CallDecision {
        val config = input.config
        val ctx = input.ctx
        val normalized = normalizePhone(ctx.rawNumber)
        val explanation = mutableListOf<String>()
        val reasons = mutableListOf<DecisionReason>()

        // 0) Modo desativado
        if (config.mode == ProtectionMode.OFF) {
            return allowDecision(normalized, listOf(DecisionReason.MODE_UNKNOWN), listOf("Proteção desativada — chamada permitida."))
        }

        // 1) Emergência — REGRA ABSOLUTA
        if (normalized.kind == PhoneKind.EMERGENCY) {
            return allowDecision(normalized, listOf(DecisionReason.EMERGENCY_NUMBER), listOf("Número de emergência — nunca é bloqueado."))
        }

        // 2) Contatos / whitelist
        val isContact = (ctx.contactName != null && ctx.contactName!!.isNotBlank()) || input.contactNumbers.contains(normalized.canonical)
        if (isContact) {
            return allowDecision(normalized, listOf(DecisionReason.CONTACT), listOf("Chamada de contato (${ctx.contactName ?? "na agenda"})."))
        }
        if (input.allowlist.contains(normalized.canonical)) {
            return allowDecision(normalized, listOf(DecisionReason.ALLOWLIST), listOf("Número na sua lista de permitidos."))
        }

        // 3) Blacklist explícita
        if (input.blacklist.contains(normalized.canonical)) {
            return CallDecision(
                action = CallAction.BLOCK,
                score = 100,
                confidence = Confidence.HIGH,
                reasons = listOf(DecisionReason.BLACKLIST),
                explanation = listOf("Número na sua lista de bloqueados."),
                normalized = normalized
            )
        }

        // 4) Regras do usuário (primeira que casa vence)
        val ruleCtx = RuleContext(isContact, ctx.at, ctx.presentation)
        val ruleEval = evaluateRules(input.rules, normalized, ruleCtx)
        if (ruleEval.matched != null && ruleEval.action != null) {
            explanation.add("Regra \"${ruleEval.matched!!.name}\" aplicada.")
            if (ruleEval.action == CallAction.ALLOW) {
                return allowDecision(normalized, listOf(DecisionReason.USER_RULE), explanation)
            }
            return CallDecision(
                action = ruleEval.action!!,
                score = if (ruleEval.action == CallAction.BLOCK) 95 else 70,
                confidence = Confidence.HIGH,
                reasons = listOf(DecisionReason.USER_RULE),
                explanation = explanation,
                normalized = normalized
            )
        }

        // 5) Tratamento específico 0303
        if (is0303(normalized) && config.handle0303 !in listOf("allow", "identify")) {
            val action = if (config.handle0303 == "block") CallAction.BLOCK else CallAction.SILENCE
            return CallDecision(
                action = action,
                score = 88,
                confidence = Confidence.MEDIUM,
                reasons = listOf(DecisionReason.BRAZIL_RULE),
                explanation = listOf("Chamada 0303 (rota de telemarketing) — tratamento configurado por você."),
                normalized = normalized
            )
        }

        // 6) Score composto
        val rep = input.lookupReputation(normalized.canonical)
        val repSignal = rep?.let { ReputationSignal(it.score, it.reports, it.confidence) }

        val campaign = detectCampaign(CampaignSignal(normalized.canonical, ctx.at), input.recentCalls, ctx.at)
        val previouslyBlocked = input.previouslyBlocked.contains(normalized.canonical)
        val previouslyAllowed = input.previouslyAllowed.contains(normalized.canonical)

        val breakdown = computeSpamScore(
            phone = normalized,
            reputation = repSignal,
            campaign = if (campaign.risk > 0) campaign else null,
            stirShaken = ctx.stirShaken,
            previouslyBlocked = previouslyBlocked,
            previouslyAllowed = previouslyAllowed
        )

        explanation.addAll(breakdown.contributions.map { "${it.label} (+${it.points})" })

        // 7) Modo AGGRESSIVE: fora dos contatos com apresentação desconhecida
        if (config.blockUnknownInAggressive && !isContact && normalized.kind == PhoneKind.INTERNATIONAL) {
            val newScore = maxOf(breakdown.score, config.blockThreshold)
            explanation.add("Modo agressivo: chamada internacional fora dos contatos.")
            // Recalcular confiança se score mudou
            val newConfidence = if (newScore >= config.blockThreshold) Confidence.MEDIUM else breakdown.confidence
            // Note: breakdown é imutável, criamos novo decisão abaixo
        }

        // 8) Decisão final
        val finalScore = if (config.blockUnknownInAggressive && !isContact && normalized.kind == PhoneKind.INTERNATIONAL)
            maxOf(breakdown.score, config.blockThreshold) else breakdown.score
        val finalConfidence = if (config.blockUnknownInAggressive && !isContact && normalized.kind == PhoneKind.INTERNATIONAL && finalScore >= config.blockThreshold)
            Confidence.MEDIUM else breakdown.confidence

        val action = actionForScore(finalScore, config, finalConfidence)

        if (action == CallAction.ALLOW && finalScore >= config.warnThreshold / 2 && breakdown.contributions.isNotEmpty()) {
            reasons.add(DecisionReason.PATTERN)
        }
        if (campaign.risk > 0) reasons.add(DecisionReason.CAMPAIGN)
        if (repSignal != null) reasons.add(DecisionReason.LOCAL_REPUTATION)
        if (ctx.stirShaken == StirShaken.FAIL) reasons.add(DecisionReason.STIR_SHAKEN)
        if (action != CallAction.ALLOW && reasons.isEmpty()) reasons.add(DecisionReason.PATTERN)

        val finalExplanation = if (action == CallAction.ALLOW && finalScore == 0)
            listOf("Nenhum sinal de risco encontrado.")
        else
            listOf("Score $finalScore/100 — ${riskBand(finalScore)}.", *explanation.toTypedArray())

        return CallDecision(
            action = action,
            score = finalScore,
            confidence = finalConfidence,
            reasons = reasons,
            explanation = finalExplanation,
            normalized = normalized
        )
    }

    private fun allowDecision(n: NormalizedPhone, reasons: List<DecisionReason>, explanation: List<String>): CallDecision {
        return CallDecision(
            action = CallAction.ALLOW,
            score = 0,
            confidence = Confidence.HIGH,
            reasons = reasons,
            explanation = explanation,
            normalized = n
        )
    }

    private fun actionForScore(score: Int, config: DecisionEngineConfig, confidence: Confidence): CallAction {
        return when {
            score >= config.blockThreshold -> {
                // BLOCK agressivo exige confiança elevada
                if (confidence == Confidence.LOW && score < config.blockThreshold + 15) CallAction.SILENCE
                else CallAction.BLOCK
            }
            score >= config.silenceThreshold -> CallAction.SILENCE
            score >= config.warnThreshold -> CallAction.WARN
            else -> CallAction.ALLOW
        }
    }

    // --- Phone Normalization ---

    fun normalizePhone(raw: String): NormalizedPhone {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        val canonical = when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+${digits.substring(2)}"
            digits.length == 10 || digits.length == 11 -> "+55$digits" // Brasil sem DDD
            else -> "+$digits"
        }

        val cleanDigits = canonical.replace("+", "")
        val (kind, countryPrefix, ddd, isBrazilian) = classifyPhone(cleanDigits)

        return NormalizedPhone(
            raw = raw,
            canonical = canonical,
            digits = cleanDigits,
            countryPrefix = countryPrefix,
            ddd = ddd,
            kind = kind,
            isBrazilian = isBrazilian
        )
    }

    private fun classifyPhone(digits: String): Pair<PhoneKind, Pair<String?, String?>> {
        // Emergência Brasil
        if (digits in setOf("190", "192", "193", "188", "199", "55190", "55192", "55193", "55188", "55199")) {
            return PhoneKind.EMERGENCY to Pair("55", null)
        }

        // Brasil
        if (digits.startsWith("55")) {
            val national = digits.substring(2)
            if (national.length >= 10) {
                val ddd = national.substring(0, 2)
                val number = national.substring(2)
                val kind = when {
                    number.startsWith("0303") || number.startsWith("0500") || number.startsWith("0900") -> PhoneKind.PREMIUM
                    number.startsWith("0800") -> PhoneKind.TOLL_FREE
                    number.startsWith("400") || number.startsWith("300") -> PhoneKind.SERVICE
                    number.length == 8 -> PhoneKind.LANDLINE
                    number.length == 9 && number.startsWith("9") -> PhoneKind.MOBILE
                    number.length <= 6 -> PhoneKind.SHORT_CODE
                    else -> PhoneKind.UNKNOWN
                }
                return kind to Pair("55", ddd)
            }
        }

        // Internacional
        return PhoneKind.INTERNATIONAL to Pair(digits.take(3), null)
    }

    fun is0303(n: NormalizedPhone): Boolean {
        return n.digits.startsWith("550303") || n.digits.startsWith("0303")
    }

    // --- Rules Engine ---

    @Serializable
    data class RuleContext(
        val isContact: Boolean,
        val at: Long,
        val presentation: CallPresentation
    )

    @Serializable
    data class RuleEval(
        val matched: Rule?,
        val action: CallAction?
    )

    fun evaluateRules(rules: List<Rule>, phone: NormalizedPhone, ctx: RuleContext): RuleEval {
        for (rule in rules.filter { it.enabled }) {
            if (matchesRule(rule, phone, ctx)) {
                return RuleEval(rule, rule.action)
            }
        }
        return RuleEval(null, null)
    }

    private fun matchesRule(rule: Rule, phone: NormalizedPhone, ctx: RuleContext): Boolean {
        return when (val m = rule.match) {
            is RuleMatch.Number -> phone.canonical == m.value
            is RuleMatch.Prefix -> phone.canonical.startsWith(m.value)
            is RuleMatch.Ddd -> phone.ddd == m.value
            is RuleMatch.Regex -> {
                try {
                    Regex(m.pattern).matches(phone.canonical)
                } catch (e: Exception) {
                    false // Regex inválida não derruba o motor
                }
            }
            is RuleMatch.Hidden -> phone.kind == PhoneKind.HIDDEN || ctx.presentation == CallPresentation.RESTRICTED
            is RuleMatch.International -> phone.kind == PhoneKind.INTERNATIONAL
            is RuleMatch.NotContact -> !ctx.isContact
            is RuleMatch.Schedule -> {
                // Simplificado: verificar horário atual
                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val local = now.toLocalDateTime(tz)
                val weekday = local.dayOfWeek.ordinal // 0=Mon...6=Sun
                val timeStr = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
                val inWeekdays = m.weekdays == null || weekday in m.weekdays
                val inTime = timeStr >= m.start && timeStr <= m.end
                inWeekdays && inTime
            }
        }
    }

    // --- Campaign Detection ---

    fun detectCampaign(current: CampaignSignal, recentCalls: List<CampaignSignal>, now: Long): CampaignDetection {
        val windowMs = 30 * 60 * 1000 // 30 min
        val similar = recentCalls.filter { call ->
            val diff = now - call.at
            diff >= 0 && diff <= windowMs && isSimilarPrefix(current.number, call.number)
        }
        val count = similar.size
        val risk = when {
            count >= 5 -> 30
            count >= 3 -> 15
            count >= 2 -> 8
            else -> 0
        }
        return CampaignDetection(risk = risk, similarCount = count)
    }

    private fun isSimilarPrefix(a: String, b: String): Boolean {
        val minLen = minOf(a.length, b.length, 8)
        return a.take(minLen) == b.take(minLen)
    }

    // --- Spam Score Computation ---

    @Serializable
    data class ReputationSignal(
        val score: Int,
        val reports: Int,
        val confidence: Double
    )

    fun computeSpamScore(
        phone: NormalizedPhone,
        reputation: ReputationSignal?,
        campaign: CampaignDetection?,
        stirShaken: StirShaken,
        previouslyBlocked: Boolean,
        previouslyAllowed: Boolean
    ): SpamScoreBreakdown {
        val contributions = mutableListOf<ScoreContribution>()
        var score = 0

        // Reputação local (peso maior)
        reputation?.let { rep ->
            val points = (rep.score * rep.confidence * 0.6).toInt()
            score += points
            contributions.add(ScoreContribution("Reputação local", points))
        }

        // Campanha
        campaign?.let { camp ->
            if (camp.risk > 0) {
                score += camp.risk
                contributions.add(ScoreContribution("Campanha detectada (${camp.similarCount} chamadas similares)", camp.risk))
            }
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

        // Padrões numéricos
        score += scorePhonePatterns(phone, contributions)

        // Confiança baseada em contribuições
        val confidence = when {
            contributions.any { it.points >= 30 } -> Confidence.HIGH
            contributions.any { it.points >= 15 } -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return SpamScoreBreakdown(
            score = score.coerceIn(0, 100),
            confidence = confidence,
            contributions = contributions
        )
    }

    private fun scorePhonePatterns(phone: NormalizedPhone, contributions: MutableList<ScoreContribution>): Int {
        var points = 0
        val digits = phone.digits

        // DDDs conhecidos de telemarketing (exemplo)
        val telemarketingDdds = setOf("11", "21", "31", "41", "51", "61", "71", "81", "85")
        if (phone.dddd?.let { it in telemarketingDdds } == true && phone.kind == PhoneKind.MOBILE) {
            // Não pontua sozinho, apenas combinado com outros sinais
        }

        // Sequenciais repetidos (ex: 99999, 12345)
        if (hasRepeatedSequence(digits) || hasSequentialDigits(digits)) {
            points += 5
            contributions.add(ScoreContribution("Padrão numérico suspeito", 5))
        }

        // Número curto (short code) não emergencial
        if (phone.kind == PhoneKind.SHORT_CODE && phone.kind != PhoneKind.EMERGENCY) {
            points += 10
            contributions.add(ScoreContribution("Código curto não emergencial", 10))
        }

        return points
    }

    private fun hasRepeatedSequence(digits: String): Boolean {
        for (i in 0..digits.length - 4) {
            val sub = digits.substring(i, i + 4)
            if (sub.distinct().size == 1) return true
        }
        return false
    }

    private fun hasSequentialDigits(digits: String): Boolean {
        val sequences = listOf("0123456789", "9876543210")
        for (seq in sequences) {
            for (i in 0..seq.length - 4) {
                if (digits.contains(seq.substring(i, i + 4))) return true
            }
        }
        return false
    }

    fun riskBand(score: Int): String = when {
        score >= 90 -> "Crítico"
        score >= 70 -> "Alto"
        score >= 50 -> "Médio"
        score >= 30 -> "Baixo"
        else -> "Mínimo"
    }
}