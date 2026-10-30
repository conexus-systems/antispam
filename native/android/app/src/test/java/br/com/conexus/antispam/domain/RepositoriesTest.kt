package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.SystemRules
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.sync.DatasetSeeder
import br.com.conexus.antispam.sync.EmbeddedSeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoriesTest {

    private val number = "+5511974018352"

    @Test
    fun settingsPersistAndMirrorIntoSystemRules() = runBlocking {
        val env = TestEnv()
        env.settings.setMode(ProtectionMode.AGGRESSIVE)
        env.settings.setNotificationsEnabled(false)
        env.settings.setBlockHidden(true)
        val s = env.settings.observe().first()
        assertEquals("AGGRESSIVE", s.mode)
        assertFalse(s.notificationsEnabled)
        assertTrue(s.blockHidden)
        assertTrue(env.rules.list().first { it.id == SystemRules.HIDDEN_ID }.enabled)
        // Padrões da spec.
        assertEquals(90, s.historyRetentionDays)
        assertFalse(s.consentShareReports)
    }

    @Test
    fun customThresholdsAreOrdered() = runBlocking {
        val env = TestEnv()
        env.settings.setCustomThresholds(warn = 80, block = 50, silence = 10)
        val s = env.settings.get()
        assertTrue(s.warnThreshold <= s.blockThreshold && s.blockThreshold <= s.silenceThreshold)
    }

    @Test
    fun invalidHandle0303IsRejected() = runBlocking {
        val env = TestEnv()
        val r = runCatching { env.settings.setHandle0303("explode") }
        assertTrue(r.isFailure)
    }

    @Test
    fun rulesValidationAndNormalization() = runBlocking {
        val env = TestEnv()
        assertTrue(env.rules.create("", RuleKind.DDD, "11", CallAction.BLOCK) is RuleResult.Invalid)
        assertTrue(env.rules.create("x", RuleKind.DDD, "1", CallAction.BLOCK) is RuleResult.Invalid)
        assertTrue(env.rules.create("x", RuleKind.REGEX, "([", CallAction.BLOCK) is RuleResult.Invalid)
        assertTrue(env.rules.create("x", RuleKind.SCHEDULE, "9|25:00|99:00", CallAction.BLOCK) is RuleResult.Invalid)
        assertEquals("+55303", env.rules.normalizeValue(RuleKind.PREFIX, "0303"))
        assertEquals("+5511 4000".replace(" ", ""), env.rules.normalizeValue(RuleKind.PREFIX, "+55 11 4000"))
        assertEquals("+55114000", env.rules.normalizeValue(RuleKind.PREFIX, "11 4000"))
        assertEquals("0,1|08:00|18:00", env.rules.normalizeValue(RuleKind.SCHEDULE, "0,1|08:00|18:00"))
    }

    @Test
    fun userRulesComeBeforeSystemRulesAndSystemRulesCannotBeDeleted() = runBlocking {
        val env = TestEnv()
        val saved = env.rules.create("DDD 21", RuleKind.DDD, "21", CallAction.SILENCE) as RuleResult.Saved
        val ordered = env.rules.list()
        assertEquals(saved.rule.id, ordered.first().id)
        assertTrue(ordered.drop(1).all { it.isSystem })
        assertFalse(env.rules.delete(SystemRules.ZERO_THREE_ZERO_ID))
        assertTrue(env.rules.delete(saved.rule.id))
        assertEquals(3, env.rules.list().size)
    }

    @Test
    fun malformedRuleIsIgnoredByEngineConversion() = runBlocking {
        val env = TestEnv()
        env.ruleDao.upsert(
            br.com.conexus.antispam.database.RuleEntity(
                name = "quebrada", kind = "SCHEDULE", value = "lixo", action = "BLOCK", priority = 1,
            ),
        )
        assertEquals(3, env.rules.toEngineRules().size)
    }

    @Test
    fun historyFilterClearAndRetention() = runBlocking {
        val env = TestEnv()
        env.lists.block(number)
        val blocked = env.pipeline.evaluate(ScreeningRequest(number))
        env.history.record(blocked.decision, atMillis = env.clock.now - 100L * DAY)
        env.history.record(env.pipeline.evaluate(ScreeningRequest("+5521974018352")).decision)
        env.history.record(blocked.decision)
        assertEquals(3, env.history.observe().first().size)
        assertEquals(2, env.history.observe(HistoryFilter.BLOCKED).first().size)
        assertEquals(1, env.history.observe(HistoryFilter.ALLOWED).first().size)
        // Retenção de 90 dias remove a de 100 dias.
        assertEquals(1, env.history.purgeExpired(90))
        assertEquals(2, env.history.observe().first().size)
        env.history.clear()
        assertTrue(env.history.observe().first().isEmpty())
    }

    @Test
    fun historyIsOrderedNewestFirst() = runBlocking {
        val env = TestEnv()
        val d = env.pipeline.evaluate(ScreeningRequest(number)).decision
        env.history.record(d, atMillis = 1_000L)
        env.history.record(d, atMillis = 3_000L)
        env.history.record(d, atMillis = 2_000L)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), env.history.observe().first().map { it.atMillis })
    }

    @Test
    fun verifyValidatesInputAndDoesNotWriteAnything() = runBlocking {
        val env = TestEnv()
        assertEquals(VerifyOutcome.Invalid("Digite um número"), env.verify(""))
        assertEquals(VerifyOutcome.Invalid("Número inválido"), env.verify("abc"))
        assertEquals(VerifyOutcome.Invalid("Inclua o DDD"), env.verify("97401 8352"))
        val ok = env.verify("(11) 97401-8352") as VerifyOutcome.Ready
        assertEquals(number, ok.result.number)
        assertEquals(CallAction.ALLOW, ok.result.decision.action)
        // Oculto simulado pela Home.
        assertTrue(env.verify("", CallPresentation.RESTRICTED) is VerifyOutcome.Ready)
        // Nada gravado.
        assertTrue(env.historyDao.state.value.isEmpty())
        assertTrue(env.blocklistDao.state.value.isEmpty())
    }

    @Test
    fun verifyUsesRealData() = runBlocking {
        val env = TestEnv()
        env.lists.block(number)
        val r = (env.verify(number) as VerifyOutcome.Ready).result
        assertEquals(CallAction.BLOCK, r.decision.action)
        assertEquals(ListStatus.BLOCKED, r.listStatus)
    }

    @Test
    fun eraseAllLocalDataRestoresDefaults() = runBlocking {
        val env = TestEnv()
        env.lists.block(number)
        env.rules.create("DDD 11", RuleKind.DDD, "11", CallAction.BLOCK)
        env.settings.setMode(ProtectionMode.AGGRESSIVE)
        env.settings.setOnboardingDone(true)
        env.settings.setBlockHidden(true)
        env.history.record(env.pipeline.evaluate(ScreeningRequest(number)).decision)

        env.privacy.eraseAllLocalData()

        assertTrue(env.historyDao.state.value.isEmpty())
        assertTrue(env.blocklistDao.state.value.isEmpty())
        val s = env.settings.get()
        assertEquals("BALANCED", s.mode)
        assertFalse(s.onboardingDone)
        assertFalse(s.blockHidden)
        assertEquals(3, env.rules.list().size)
        assertEquals(1, env.prefs.cleared)
        assertEquals(EmbeddedSeed.VERSION, env.metaDao.getSync()!!.version)
        // E o app volta a decidir normalmente.
        assertEquals(CallAction.SILENCE, env.pipeline.evaluate(ScreeningRequest("03031234567")).decision.action)
    }

    @Test
    fun embeddedSeedRegistersDatasetMeta() = runBlocking {
        val env = TestEnv()
        DatasetSeeder(env.reputationDao, env.metaDao, env.settingsDao).applyEmbedded(env.clock.now)
        val meta = env.metaDao.getSync()!!
        assertEquals("seed", meta.source)
        assertEquals(EmbeddedSeed.VERSION, meta.version)
        assertTrue(meta.signatureOk)
        assertEquals(EmbeddedSeed.VERSION, env.settings.get().datasetVersion)
        val info = env.settings.observeDataset().first()
        assertNotNull(info)
        assertEquals(EmbeddedSeed.VERSION, info!!.version)
    }

    @Test
    fun isoTimeIsSortableAndRoundTrips() {
        val a = IsoTime.format(1_000L)
        val b = IsoTime.format(1_100L)
        assertTrue(a < b)
        assertEquals(1_100L, IsoTime.parse(b))
        assertEquals(0L, IsoTime.parse("lixo"))
    }

    @Test
    fun phoneFormatting() {
        fun d(raw: String) = PhoneFormat.display(br.com.conexus.antispam.decision.PhoneNormalizer.normalize(raw))
        assertEquals("+55 (11) 91234-5678", d("11912345678"))
        assertEquals("+55 (11) 4002-8922", d("1140028922"))
        assertEquals("0303 123 4567", d("03031234567"))
        assertEquals("Número oculto", d(""))
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
