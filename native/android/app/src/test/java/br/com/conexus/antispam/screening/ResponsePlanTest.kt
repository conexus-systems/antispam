package br.com.conexus.antispam.screening

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.DecisionEngine
import br.com.conexus.antispam.decision.DecisionReason
import br.com.conexus.antispam.domain.ResponsePolicy
import br.com.conexus.antispam.domain.ListStatus
import br.com.conexus.antispam.domain.ScreeningRequest
import br.com.conexus.antispam.domain.ScreeningResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsePlanTest {

    private fun result(raw: String, action: CallAction, reject: Boolean = true): ScreeningResult {
        val n = DecisionEngine.normalizePhone(raw)
        val d = CallDecision(action, 90, Confidence.HIGH, listOf(DecisionReason.BLACKLIST), listOf("x"), n)
        return ScreeningResult(
            ScreeningRequest(raw), d, raw, raw, "", "", "", d.explanation, ListStatus.NONE, ResponsePolicy(reject, true), 0L,
        )
    }

    @Test
    fun blockDisallowsAndRejects() {
        val p = ResponsePlan.from(result("+5511912345678", CallAction.BLOCK))
        assertTrue(p.disallowCall); assertTrue(p.rejectCall); assertFalse(p.silenceCall)
        assertTrue(p.skipNotification); assertFalse(p.skipCallLog)
    }

    @Test
    fun blockWithoutRejectSetting() {
        val p = ResponsePlan.from(result("+5511912345678", CallAction.BLOCK, reject = false))
        assertTrue(p.disallowCall); assertFalse(p.rejectCall)
    }

    @Test
    fun silenceOnlySilences() {
        val p = ResponsePlan.from(result("+5511912345678", CallAction.SILENCE))
        assertEquals(ResponsePlan.ALLOW.copy(silenceCall = true), p)
    }

    @Test
    fun warnAndAllowPass() {
        assertEquals(ResponsePlan.ALLOW, ResponsePlan.from(result("+5511912345678", CallAction.WARN)))
        assertEquals(ResponsePlan.ALLOW, ResponsePlan.from(result("+5511912345678", CallAction.ALLOW)))
    }

    @Test
    fun emergencyIsNeverBlockedEvenIfDecisionSaysBlock() {
        assertEquals(ResponsePlan.ALLOW, ResponsePlan.from(result("190", CallAction.BLOCK)))
        assertEquals(ResponsePlan.ALLOW, ResponsePlan.from(result("192", CallAction.SILENCE)))
    }
}
