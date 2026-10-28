package br.com.conexus.antispam.decision

/** Modo de proteção escolhido pelo usuário. CUSTOM usa os limiares do usuário sobre a base BALANCED. */
enum class ProtectionMode { OFF, BASIC, BALANCED, AGGRESSIVE, CUSTOM }

enum class CallAction { ALLOW, WARN, SILENCE, BLOCK }

enum class Confidence { LOW, MEDIUM, HIGH }

enum class DecisionReason {
    MODE_UNKNOWN,
    EMERGENCY_NUMBER,
    CONTACT,
    ALLOWLIST,
    BLACKLIST,
    USER_RULE,
    BRAZIL_RULE,
    LOCAL_REPUTATION,
    CAMPAIGN,
    STIR_SHAKEN,
    PATTERN,
    FAILSAFE,
}

/** Status STIR/SHAKEN. UNKNOWN equivale ao NotVerified do Rust. */
enum class StirShaken { PASS, FAIL, UNKNOWN }

/** Apresentação do número. PAYPHONE não existe no Rust; tratado como não restrito. */
enum class CallPresentation { ALLOWED, PAYPHONE, RESTRICTED, UNKNOWN }

data class DecisionEngineConfig(
    val mode: ProtectionMode,
    val blockThreshold: Int,
    val silenceThreshold: Int,
    val warnThreshold: Int,
    /** "allow", "identify", "silence" ou "block". */
    val handle0303: String,
    val blockUnknownInAggressive: Boolean,
)

data class CallContext(
    val rawNumber: String,
    val contactName: String? = null,
    val presentation: CallPresentation = CallPresentation.UNKNOWN,
    val stirShaken: StirShaken = StirShaken.UNKNOWN,
    /** Instante da chamada em epoch millis. */
    val at: Long = System.currentTimeMillis(),
)

/** Sinal de chamada recente usado na detecção de campanha. */
data class CampaignSignal(
    val number: String,
    val at: Long,
)

/** Agregado de reputação local guardado no banco. */
data class ReputationEntry(
    val key: String,
    val country: String,
    val category: String,
    val score: Int,
    val reports: Int,
    val uniqueReporters: Int,
    val firstSeen: String,
    val lastSeen: String,
    val confidence: Double,
    val falsePositives: Int,
)

sealed class RuleMatch {
    data class ByNumber(val value: String) : RuleMatch()
    data class ByPrefix(val value: String) : RuleMatch()
    data class ByDdd(val value: String) : RuleMatch()
    data class ByRegex(val pattern: String) : RuleMatch()
    object Hidden : RuleMatch()
    object International : RuleMatch()
    object NotContact : RuleMatch()
    /** weekdays: 0=segunda ... 6=domingo. null = todos os dias. start/end em "HH:MM". */
    data class BySchedule(val weekdays: List<Int>?, val start: String, val end: String) : RuleMatch()
}

data class Rule(
    val name: String,
    val enabled: Boolean,
    val match: RuleMatch,
    val action: CallAction,
)

data class EngineInput(
    val config: DecisionEngineConfig,
    val ctx: CallContext,
    val contactNumbers: Collection<String> = emptySet(),
    val allowlist: Collection<String> = emptySet(),
    val blacklist: Collection<String> = emptySet(),
    val rules: List<Rule> = emptyList(),
    val recentCalls: List<CampaignSignal> = emptyList(),
    val previouslyBlocked: Collection<String> = emptySet(),
    val previouslyAllowed: Collection<String> = emptySet(),
    /** Consulta o agregado de reputação pelo E.164 canônico. Suspende porque o DAO é suspend. */
    val lookupReputation: suspend (String) -> ReputationEntry? = { null },
)

data class CallDecision(
    val action: CallAction,
    val score: Int,
    val confidence: Confidence,
    val reasons: List<DecisionReason>,
    val explanation: List<String>,
    val normalized: NormalizedNumber,
)
