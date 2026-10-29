package br.com.conexus.antispam.domain

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.DecisionReason
import br.com.conexus.antispam.decision.ProtectionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningPipelineTest {

    private val spammer = "+5511974018352"
    private val zeroThreeZero = "03031234567"

    private fun TestEnv.decide(raw: String, presentation: CallPresentation = CallPresentation.UNKNOWN) =
        runBlocking { pipeline.evaluate(ScreeningRequest(raw, presentation)) }

    @Test
    fun cleanUnknownNumberIsAllowed() = runBlocking {
        val env = TestEnv()
        val r = env.decide(spammer)
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertEquals("Nenhum sinal de risco encontrado.", r.explanation.first())
        assertEquals("+55 (11) 97401-8352", r.displayNumber)
        assertEquals(ListStatus.NONE, r.listStatus)
    }

    @Test
    fun zeroThreeZeroIsSilencedByDefaultRule() {
        val env = TestEnv()
        val r = env.decide(zeroThreeZero)
        assertEquals(CallAction.SILENCE, r.decision.action)
        assertTrue(r.decision.reasons.contains(DecisionReason.USER_RULE))
        assertTrue(r.explanation.first().contains("0303"))
    }

    @Test
    fun zeroThreeZeroHandlingFollowsSetting() = runBlocking {
        val env = TestEnv()
        env.settings.setHandle0303("block")
        assertEquals(CallAction.BLOCK, env.decide(zeroThreeZero).decision.action)
        env.settings.setHandle0303("identify")
        assertEquals(CallAction.WARN, env.decide(zeroThreeZero).decision.action)
        env.settings.setHandle0303("allow")
        assertEquals(CallAction.ALLOW, env.decide(zeroThreeZero).decision.action)
        env.settings.setHandle0303("silence")
        assertEquals(CallAction.SILENCE, env.decide(zeroThreeZero).decision.action)
    }

    @Test
    fun emergencyIsNeverBlockedEvenInAggressiveWithBlockEverythingRule() = runBlocking {
        val env = TestEnv()
        env.settings.setMode(ProtectionMode.AGGRESSIVE)
        env.rules.create("Bloquear tudo", RuleKind.NOT_CONTACT, "", CallAction.BLOCK)
        env.settings.setBlockHidden(true)
        // Bloqueio de 190 na lista é recusado e, mesmo que existisse, o motor permite.
        assertEquals(ListResult.Emergency, env.lists.block("190"))
        val r = env.decide("190")
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertTrue(r.decision.reasons.contains(DecisionReason.EMERGENCY_NUMBER))
        // Sanidade: outro número cai na regra.
        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
    }

    @Test
    fun blockedNumberIsBlockedAndExplained() = runBlocking {
        val env = TestEnv()
        assertTrue(env.lists.block("(11) 97401-8352") is ListResult.Added)
        val r = env.decide(spammer)
        assertEquals(CallAction.BLOCK, r.decision.action)
        assertEquals(ListStatus.BLOCKED, r.listStatus)
        assertEquals("Número na sua lista de bloqueados.", r.explanation.first())
        assertEquals("Provável spam", r.headline)
    }

    @Test
    fun allowedNumberBeatsRule() = runBlocking {
        val env = TestEnv()
        env.rules.create("Prefixo 11 9740", RuleKind.PREFIX, "11 9740", CallAction.BLOCK)
        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
        env.lists.allow(spammer)
        val r = env.decide(spammer)
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertTrue(r.decision.reasons.contains(DecisionReason.ALLOWLIST))
    }

    @Test
    fun prefixAndDddRulesApplyAndFirstByPriorityWins() = runBlocking {
        val env = TestEnv()
        env.rules.create("DDD 11 silenciar", RuleKind.DDD, "11", CallAction.SILENCE)
        env.rules.create("Prefixo bloquear", RuleKind.PREFIX, "+5511 9740", CallAction.BLOCK)
        // Primeira criada vale primeiro.
        assertEquals(CallAction.SILENCE, env.decide(spammer).decision.action)
        val ids = env.rules.list().filter { !it.isSystem }.map { it.id }
        env.rules.reorder(ids.reversed())
        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
        // Outro DDD não é afetado.
        assertEquals(CallAction.ALLOW, env.decide("+5521974018352").decision.action)
    }

    @Test
    fun disabledRuleDoesNotAct() = runBlocking {
        val env = TestEnv()
        val saved = env.rules.create("DDD 11", RuleKind.DDD, "11", CallAction.BLOCK) as RuleResult.Saved
        env.rules.setEnabled(saved.rule.id, false)
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
    }

    @Test
    fun localReportBlocksNextCallAndMarksHistory() = runBlocking {
        val env = TestEnv()
        val first = env.decide(spammer)
        assertEquals(CallAction.ALLOW, first.decision.action)
        env.pipeline.record(first)

        val res = env.reports.report(spammer, ReportCategory.SCAM)
        assertTrue(res is ReportResult.Saved)

        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
        val entry = env.lists.get(spammer)!!
        assertEquals("report", entry.source)
        assertEquals("Golpe", entry.note)
        val rep = env.reputationDao.get(spammer)!!
        assertEquals(1, rep.reports)
        assertEquals("fraud", rep.category)
        val item = env.history.observe().first().single()
        assertTrue(item.reported)
        assertEquals("Golpe", item.category)

        // Denunciar de novo não duplica.
        assertTrue(env.reports.report(spammer, ReportCategory.SCAM) is ReportResult.AlreadyReported)
        assertEquals(1, env.reputationDao.get(spammer)!!.reports)
    }

    @Test
    fun undoReportRestoresEverything() = runBlocking {
        val env = TestEnv()
        env.pipeline.record(env.decide(spammer))
        val saved = env.reports.report(spammer, ReportCategory.TELEMARKETING) as ReportResult.Saved
        env.reports.undo(saved.receipt)
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
        assertEquals(null, env.reputationDao.get(spammer))
        assertFalse(env.history.observe().first().single().reported)
    }

    @Test
    fun reportingAllowedNumberMovesItToBlockedAndUndoRestoresAllowed() = runBlocking {
        val env = TestEnv()
        env.lists.allow(spammer)
        val saved = env.reports.report(spammer, ReportCategory.OTHER) as ReportResult.Saved
        assertEquals(ListStatus.BLOCKED, env.lists.status(spammer))
        env.reports.undo(saved.receipt)
        assertEquals(ListStatus.ALLOWED, env.lists.status(spammer))
    }

    @Test
    fun unblockStopsBlockingAndListsAreExclusive() = runBlocking {
        val env = TestEnv()
        env.lists.block(spammer)
        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
        val moved = env.lists.allow(spammer)
        assertTrue(moved is ListResult.Added && moved.previous?.action == "block")
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
        assertTrue(env.lists.observeBlocked().first().isEmpty())
        assertEquals(1, env.lists.observeAllowed().first().size)
        // Desfazer volta a bloquear.
        env.lists.undo(moved as ListResult.Added)
        assertEquals(CallAction.BLOCK, env.decide(spammer).decision.action)
    }

    @Test
    fun listValidation() = runBlocking {
        val env = TestEnv()
        assertEquals(ListResult.Invalid, env.lists.block("abc"))
        assertEquals(ListResult.Invalid, env.lists.block("1234"))
        assertTrue(env.lists.block(spammer) is ListResult.Added)
        assertEquals(ListResult.Duplicate, env.lists.block("(11) 97401-8352"))
    }

    @Test
    fun hiddenNumbersFollowSetting() = runBlocking {
        val env = TestEnv()
        val hidden = { env.decide("", CallPresentation.RESTRICTED).decision.action }
        assertEquals(CallAction.ALLOW, hidden())
        env.settings.setBlockHidden(true)
        assertEquals(CallAction.BLOCK, hidden())
        env.settings.setBlockHidden(false)
        assertEquals(CallAction.ALLOW, hidden())
    }

    @Test
    fun internationalNumbersFollowSetting() = runBlocking {
        val env = TestEnv()
        val intl = "+14155550123"
        assertEquals(CallAction.ALLOW, env.decide(intl).decision.action)
        env.settings.setBlockInternational(true)
        assertEquals(CallAction.BLOCK, env.decide(intl).decision.action)
        // Brasileiro não é afetado.
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
    }

    @Test
    fun protectionOffAllowsEverything() = runBlocking {
        val env = TestEnv()
        env.lists.block(spammer)
        env.settings.setMode(ProtectionMode.OFF)
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
        assertEquals(CallAction.ALLOW, env.decide(zeroThreeZero).decision.action)
    }

    @Test
    fun highReputationFromDatasetBlocksInBalancedMode() = runBlocking {
        val env = TestEnv()
        env.reputationDao.upsert(
            br.com.conexus.antispam.database.ReputationEntry(
                key = spammer, category = "fraud", score = 100, reports = 40, uniqueReporters = 40,
                firstSeen = "", lastSeen = "", confidence = 0.9,
            ),
        )
        val r = env.decide(spammer)
        assertEquals(CallAction.BLOCK, r.decision.action)
        assertTrue(r.decision.reasons.contains(DecisionReason.LOCAL_REPUTATION))
    }

    @Test
    fun recordStoresFullDecisionAndSurvivesRoundTrip() = runBlocking {
        val env = TestEnv()
        env.lists.block(spammer)
        val result = env.decide(spammer)
        val saved = env.pipeline.record(result)
        val loaded = env.history.get(saved.id)!!
        assertEquals(CallAction.BLOCK, loaded.action)
        assertEquals(100, loaded.score)
        assertEquals(listOf(DecisionReason.BLACKLIST), loaded.reasons)
        assertEquals(listOf("Número na sua lista de bloqueados."), loaded.explanation)
        assertEquals(spammer, loaded.number)
        assertEquals(env.clock.now, loaded.atMillis)
        assertFalse(loaded.simulated)
        assertEquals(1, env.history.observeCounts().first().blocked)
    }

    @Test
    fun failSafeIsAllowWithFailsafeReason() {
        val env = TestEnv()
        val r = env.pipeline.failSafe(ScreeningRequest(spammer), "tempo esgotado")
        assertEquals(CallAction.ALLOW, r.decision.action)
        assertTrue(r.decision.reasons.contains(DecisionReason.FAILSAFE))
        assertTrue(r.explanation.first().contains("tempo esgotado"))
    }

    @Test
    fun campaignSignalsComeFromRealHistoryOnly() = runBlocking {
        val env = TestEnv()
        // 5 chamadas reais de prefixo parecido pesam; simuladas não.
        repeat(5) { i ->
            env.history.record(
                env.decide("+551197401835$i").decision,
                atMillis = env.clock.now - 1000L * (i + 1),
                simulated = true,
            )
        }
        assertEquals(CallAction.ALLOW, env.decide(spammer).decision.action)
        assertEquals(0, env.decide(spammer).decision.score)
    }
}
