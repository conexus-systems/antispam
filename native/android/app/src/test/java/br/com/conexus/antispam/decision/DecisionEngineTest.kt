package br.com.conexus.antispam.decision

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Espelha os testes de rust/antispam-core/src/decision/mod.rs. */
class DecisionEngineTest {

    private fun makeInput(raw: String): EngineInput = EngineInput(
        config = DecisionEngine.MODE_DEFAULTS.getValue(ProtectionMode.BALANCED),
        ctx = CallContext(
            rawNumber = raw,
            contactName = null,
            presentation = CallPresentation.ALLOWED,
            stirShaken = StirShaken.UNKNOWN,
            at = System.currentTimeMillis(),
        ),
    )

    private fun decide(input: EngineInput): CallDecision = runBlocking { DecisionEngine.decide(input) }

    @Test
    fun testEmergency() {
        val decision = decide(makeInput("190"))
        assertEquals(CallAction.ALLOW, decision.action)
        assertTrue(decision.reasons.contains(DecisionReason.EMERGENCY_NUMBER))
    }

    @Test
    fun testAllowlist() {
        val input = makeInput("+5511987654321").copy(allowlist = setOf("+5511987654321"))
        val decision = decide(input)
        assertEquals(CallAction.ALLOW, decision.action)
        assertTrue(decision.reasons.contains(DecisionReason.ALLOWLIST))
    }

    @Test
    fun testBlacklist() {
        val input = makeInput("+5511987654321").copy(blacklist = setOf("+5511987654321"))
        val decision = decide(input)
        assertEquals(CallAction.BLOCK, decision.action)
        assertEquals(100, decision.score)
    }

    @Test
    fun test0303Balanced() {
        val decision = decide(makeInput("03031234567"))
        assertEquals(CallAction.SILENCE, decision.action)
        assertTrue(decision.reasons.contains(DecisionReason.BRAZIL_RULE))
    }

    @Test
    fun test0303AggressiveBlock() {
        val input = makeInput("03031234567").copy(
            config = DecisionEngine.MODE_DEFAULTS.getValue(ProtectionMode.AGGRESSIVE),
        )
        val decision = decide(input)
        assertEquals(CallAction.BLOCK, decision.action)
    }
}
