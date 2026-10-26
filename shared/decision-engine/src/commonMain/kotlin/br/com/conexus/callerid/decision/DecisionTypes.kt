package br.com.conexus.callerid.decision

import kotlinx.serialization.Serializable

@Serializable
enum class ProtectionMode {
    OFF, BASIC, BALANCED, AGGRESSIVE, CUSTOM
}

@Serializable
enum class CallAction {
    ALLOW, WARN, SILENCE, BLOCK
}

@Serializable
enum class Confidence {
    LOW, MEDIUM, HIGH
}

@Serializable
enum class PhoneKind {
    MOBILE, LANDLINE, SERVICE, TOLL_FREE, PREMIUM, SHORT_CODE, EMERGENCY, INTERNATIONAL, UNKNOWN, HIDDEN
}

@Serializable
enum class StirShaken {
    PASS, FAIL, UNKNOWN
}

@Serializable
enum class CallPresentation {
    ALLOWED, PAYPHONE, RESTRICTED, UNKNOWN
}

@Serializable
data class NormalizedPhone(
    val raw: String,
    val canonical: String,
    val digits: String,
    val countryPrefix: String? = null,
    val ddd: String? = null,
    val kind: PhoneKind,
    val isBrazilian: Boolean
)

@Serializable
data class CallContext(
    val rawNumber: String,
    val presentation: CallPresentation = CallPresentation.UNKNOWN,
    val contactName: String? = null,
    val stirShaken: StirShaken = StirShaken.UNKNOWN,
    val at: Long = System.currentTimeMillis() // epoch millis
)

@Serializable
enum class DecisionReason {
    EMERGENCY_NUMBER, CONTACT, ALLOWLIST, USER_RULE, BLACKLIST,
    LOCAL_REPUTATION, PATTERN, CAMPAIGN, BRAZIL_RULE, STIR_SHAKEN, MODE_UNKNOWN, FAILSAFE
}

@Serializable
data class CallDecision(
    val action: CallAction,
    val score: Int,
    val confidence: Confidence,
    val reasons: List<DecisionReason>,
    val explanation: List<String>,
    val normalized: NormalizedPhone
)

@Serializable
data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val action: CallAction,
    val match: RuleMatch
)

@Serializable
sealed class RuleMatch {
    @Serializable data class Number(val value: String) : RuleMatch()
    @Serializable data class Prefix(val value: String) : RuleMatch()
    @Serializable data class Ddd(val value: String) : RuleMatch()
    @Serializable data class Regex(val pattern: String) : RuleMatch()
    @Serializable object Hidden : RuleMatch()
    @Serializable object International : RuleMatch()
    @Serializable object NotContact : RuleMatch()
    @Serializable data class Schedule(val start: String, val end: String, val weekdays: List<Int>?) : RuleMatch()
}

@Serializable
data class DecisionEngineConfig(
    val mode: ProtectionMode,
    val blockThreshold: Int,
    val silenceThreshold: Int,
    val warnThreshold: Int,
    val handle0303: String, // allow, identify, silence, block
    val blockUnknownInAggressive: Boolean
)

@Serializable
data class EngineInput(
    val ctx: CallContext,
    val config: DecisionEngineConfig,
    val rules: List<Rule>,
    val allowlist: List<String>,
    val blacklist: List<String>,
    val lookupReputation: (String) -> ReputationEntry?,
    val recentCalls: List<CampaignSignal>,
    val contactNumbers: Set<String> = emptySet(),
    val previouslyBlocked: Set<String> = emptySet(),
    val previouslyAllowed: Set<String> = emptySet()
)

@Serializable
data class ReputationEntry(
    val key: String,
    val country: String = "BR",
    val category: String,
    val score: Int,
    val reports: Int,
    val uniqueReporters: Int,
    val firstSeen: String,
    val lastSeen: String,
    val confidence: Double = 0.5,
    val falsePositives: Int = 0
)

@Serializable
data class CampaignSignal(
    val number: String,
    val at: Long
)

@Serializable
data class CampaignDetection(
    val risk: Int,
    val similarCount: Int
)

@Serializable
data class SpamScoreBreakdown(
    val score: Int,
    val confidence: Confidence,
    val contributions: List<ScoreContribution>
)

@Serializable
data class ScoreContribution(
    val label: String,
    val points: Int
)