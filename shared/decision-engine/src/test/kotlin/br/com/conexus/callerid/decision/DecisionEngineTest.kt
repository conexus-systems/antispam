package br.com.conexus.callerid.decision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun ctx(raw: String): CallContext {
        return CallContext(
            rawNumber = raw,
            presentation = CallPresentation.UNKNOWN,
            contactName = null,
            stirShaken = StirShaken.UNKNOWN
        )
    }

    private fun baseInput(over: (EngineInput.() -> Unit)? = null): EngineInput {
        val input = EngineInput(
            ctx = ctx("+5511999999999"),
            config = DecisionEngine.MODE_DEFAULTS[ProtectionMode.BALANCED]!!,
            rules = emptyList(),
            allowlist = emptyList(),
            blacklist = emptyList(),
            lookupReputation = { null },
            recentCalls = emptyList(),
        )
        over?.let { input(it) }
        return input
    }

    @Test
    fun `emergency 190 never blocked`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("190")
            config = DecisionEngine.MODE_DEFAULTS[ProtectionMode.AGGRESSIVE]!!
            blacklist = listOf("190")
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.EMERGENCY_NUMBER))
    }

    @Test
    fun `emergency 192 never blocked`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("192")
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.EMERGENCY_NUMBER))
    }

    @Test
    fun `emergency 193 never blocked`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("193")
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.EMERGENCY_NUMBER))
    }

    @Test
    fun `whitelist beats any score`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            allowlist = listOf("+5511999999999")
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.ALLOWLIST))
    }

    @Test
    fun `blacklist blocks with score 100`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            blacklist = listOf("+5511999999999")
        })
        assertEquals(CallAction.BLOCK, d.action)
        assertTrue(d.reasons.contains(DecisionReason.BLACKLIST))
        assertEquals(100, d.score)
    }

    @Test
    fun `mode OFF allows everything`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            config = DecisionEngine.MODE_DEFAULTS[ProtectionMode.OFF]!!
            blacklist = listOf("+5511999999999")
        })
        assertEquals(CallAction.ALLOW, d.action)
    }

    @Test
    fun `BALANCED blocks high reputation`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+551140028922")
            lookupReputation = { c ->
                if (c == "+551140028922") ReputationEntry(
                    key = c,
                    country = "BR",
                    category = "telemarketing",
                    score = 92,
                    reports = 150,
                    uniqueReporters = 120,
                    firstSeen = "",
                    lastSeen = "2026-10-20T14:30:00Z",
                    confidence = 0.9,
                    falsePositives = 0
                ) else null
            }
        })
        assertTrue(listOf(CallAction.BLOCK, CallAction.SILENCE).contains(d.action))
        assertTrue(d.score > 70)
        assertTrue(d.explanation.joinToString(" ").contains("Reputação"))
    }

    @Test
    fun `single report does not block`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5531999991234")
            lookupReputation = { c ->
                if (c == "+5531999991234") ReputationEntry(
                    key = c,
                    country = "BR",
                    category = "spam",
                    score = 60,
                    reports = 1,
                    uniqueReporters = 1,
                    firstSeen = "",
                    lastSeen = "2026-10-20T14:30:00Z",
                    confidence = 0.2,
                    falsePositives = 0
                ) else null
            }
        })
        assertEquals(CallAction.ALLOW, d.action)
    }

    @Test
    fun `user rule prefix blocks before score`() = runTest {
        val rule = Rule(
            id = "r1",
            name = "Bloqueia 4002",
            enabled = true,
            action = CallAction.BLOCK,
            match = RuleMatch.Prefix(value = "+55114002")
        )
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("1140028922")
            rules = listOf(rule)
        })
        assertEquals(CallAction.BLOCK, d.action)
        assertTrue(d.reasons.contains(DecisionReason.USER_RULE))
    }

    @Test
    fun `invalid regex rule does not crash engine`() = runTest {
        val rule = Rule(
            id = "r2",
            name = "Regex ruim",
            enabled = true,
            action = CallAction.BLOCK,
            match = RuleMatch.Regex(pattern = "([invalid")
        )
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("1130614000")
            rules = listOf(rule)
        })
        assertEquals(CallAction.ALLOW, d.action)
    }

    @Test
    fun `fail-safe: lookup throws -> ALLOW`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5511987654321")
            lookupReputation = { throw Exception("DB corrompido") }
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.FAILSAFE))
    }

    @Test
    fun `0303 silenced in BALANCED`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("03033130303")
        })
        assertEquals(CallAction.SILENCE, d.action)
        assertTrue(d.reasons.contains(DecisionReason.BRAZIL_RULE))
    }

    @Test
    fun `0303 allowed when configured`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("03033130303")
            config = DecisionEngineConfig(
                mode = ProtectionMode.CUSTOM,
                blockThreshold = 80,
                silenceThreshold = 90,
                warnThreshold = 60,
                handle0303 = "allow",
                blockUnknownInAggressive = false
            )
        })
        assertEquals(CallAction.ALLOW, d.action)
    }

    @Test
    fun `campaign detected adds score`() = runTest {
        val now = java.time.Instant.parse("2026-06-01T15:00:00Z").toEpochMilli()
        val recent = listOf(
            CampaignSignal("+551140028922", now - 60_000),
            CampaignSignal("+551140028923", now - 120_000),
            CampaignSignal("+551140028924", now - 180_000),
        )
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+551140028999")
            recentCalls = recent
        })
        assertTrue(d.score > 0)
        val combined = d.explanation.joinToString(" ") + d.reasons.joinToString(" ")
        assertTrue(combined.contains("campanha", ignoreCase = true) || combined.contains("Campanha"))
    }

    @Test
    fun `AGGRESSIVE blocks international`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+14155552671")
            config = DecisionEngine.MODE_DEFAULTS[ProtectionMode.AGGRESSIVE]!!
        })
        assertEquals(CallAction.BLOCK, d.action)
    }

    @Test
    fun `STIR SHAKEN FAIL adds score`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5511999999999").copy(stirShaken = StirShaken.FAIL)
        })
        assertTrue(d.score >= 25)
        assertTrue(d.reasons.contains(DecisionReason.STIR_SHAKEN))
    }

    @Test
    fun `STIR SHAKEN PASS reduces score`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5511999999999").copy(stirShaken = StirShaken.PASS)
            lookupReputation = { c ->
                if (c == "+5511999999999") ReputationEntry(
                    key = c, country = "BR", category = "telemarketing",
                    score = 50, reports = 10, uniqueReporters = 8,
                    firstSeen = "", lastSeen = "2026-10-20T14:30:00Z",
                    confidence = 0.5, falsePositives = 0
                ) else null
            }
        })
        // PASS dá -10, mas reputação 50 * 0.5 * 0.6 = 15 → total ~5
        assertTrue(d.score < 50)
    }

    @Test
    fun `previously blocked boosts score`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            previouslyBlocked = setOf("+5511999999999")
        })
        assertTrue(d.score >= 30)
        assertTrue(d.reasons.contains(DecisionReason.PATTERN) || d.explanation.joinToString(" ").contains("Bloqueado anteriormente"))
    }

    @Test
    fun `previously allowed reduces score`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            previouslyAllowed = setOf("+5511999999999")
            lookupReputation = { c ->
                if (c == "+5511999999999") ReputationEntry(
                    key = c, country = "BR", category = "telemarketing",
                    score = 50, reports = 10, uniqueReporters = 8,
                    firstSeen = "", lastSeen = "2026-10-20T14:30:00Z",
                    confidence = 0.5, falsePositives = 0
                ) else null
            }
        })
        assertTrue(d.score < 50)
    }

    @Test
    fun `contact in address book allows`() = runTest {
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5511999999999").copy(contactName = "Mãe")
        })
        assertEquals(CallAction.ALLOW, d.action)
        assertTrue(d.reasons.contains(DecisionReason.CONTACT))
    }

    @Test
    fun `hidden presentation with rule blocks`() = runTest {
        val rule = Rule(
            id = "r3",
            name = "Bloqueia ocultos",
            enabled = true,
            action = CallAction.BLOCK,
            match = RuleMatch.Hidden
        )
        val d = DecisionEngine.decide(baseInput {
            ctx = ctx("+5511999999999").copy(presentation = CallPresentation.RESTRICTED)
            rules = listOf(rule)
        })
        assertEquals(CallAction.BLOCK, d.action)
        assertTrue(d.reasons.contains(DecisionReason.USER_RULE))
    }
}