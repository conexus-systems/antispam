package br.com.conexus.antispam.ui

import br.com.conexus.antispam.domain.HistoryFilter
import br.com.conexus.antispam.domain.ReportCategory
import br.com.conexus.antispam.domain.TestEnv
import br.com.conexus.antispam.domain.VerifyOutcome
import br.com.conexus.antispam.ui.history.HistoryTab
import br.com.conexus.antispam.ui.history.ListActions
import br.com.conexus.antispam.ui.history.NoticeKind
import br.com.conexus.antispam.ui.history.RelativeTime
import br.com.conexus.antispam.ui.history.RelativeTimes
import br.com.conexus.antispam.ui.history.UndoAction
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HistoryVerifyLogicTest {

    private val env = TestEnv()
    private val actions = ListActions(env.lists, env.reports)
    private val number = "(11) 91234-5678"

    @Test fun `block, then unblock, then undo restore the lists`() = runBlocking {
        val blocked = actions.block(number)
        assertEquals(NoticeKind.BLOCKED, blocked.kind)
        assertEquals(NoticeKind.ALREADY_BLOCKED, actions.block(number).kind)

        val unblocked = actions.allow(number, wasBlocked = true)
        assertEquals(NoticeKind.UNBLOCKED, unblocked.kind)
        assertTrue(env.lists.observeBlocked().first().isEmpty())
        assertEquals(1, env.lists.observeAllowed().first().size)

        assertEquals(NoticeKind.UNDONE, actions.undo(unblocked.undo!!).kind)
        assertEquals(1, env.lists.observeBlocked().first().size)
        assertTrue(env.lists.observeAllowed().first().isEmpty())
    }

    @Test fun `report blocks the number and undo reverts it`() = runBlocking {
        val n = actions.report(number, ReportCategory.SCAM)
        assertEquals(NoticeKind.REPORTED, n.kind)
        assertIs<UndoAction.Report>(n.undo)
        assertEquals(1, env.lists.observeBlocked().first().size)
        assertEquals(NoticeKind.ALREADY_REPORTED, actions.report(number, ReportCategory.SCAM).kind)
        actions.undo(n.undo!!)
        assertTrue(env.lists.observeBlocked().first().isEmpty())
    }

    @Test fun `hidden, invalid and emergency numbers are refused`() = runBlocking {
        assertFalse(actions.isActionable(""))
        assertEquals(NoticeKind.HIDDEN, actions.block("").kind)
        assertEquals(NoticeKind.HIDDEN, actions.report("private", ReportCategory.OTHER).kind)
        assertEquals(NoticeKind.EMERGENCY, actions.block("190").kind)
        assertNull(actions.block("").undo)
    }

    @Test fun `verify does not write history and invalid input returns a message`() = runBlocking {
        val bad = env.verify("12")
        assertIs<VerifyOutcome.Invalid>(bad)
        val ok = env.verify(number)
        assertIs<VerifyOutcome.Ready>(ok)
        assertTrue(env.history.observe(HistoryFilter.ALL).first().isEmpty())
    }

    @Test fun `history tabs filter reported on top of all`() = runBlocking {
        val r = env.verify("(11) 4002-8922") as VerifyOutcome.Ready
        env.history.record(r.result.decision)
        val all = env.history.observe(HistoryTab.ALL.source).first()
        assertEquals(1, all.size)
        assertTrue(HistoryTab.REPORTED.apply(all).isEmpty())
        assertEquals(HistoryFilter.ALL, HistoryTab.REPORTED.source)
    }

    @Test fun `relative time buckets`() {
        val tz = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = 1_800_000_000_000L // 2027-01-15 08:00:00 UTC
        assertEquals(RelativeTime.Now, RelativeTimes.of(now - 10_000, now, tz))
        assertEquals(RelativeTime.Now, RelativeTimes.of(now + 5_000, now, tz))
        assertEquals(RelativeTime.MinutesAgo(5), RelativeTimes.of(now - 5 * 60_000, now, tz))
        assertEquals(RelativeTime.HoursAgo(2), RelativeTimes.of(now - 2 * 3_600_000L, now, tz))
        assertEquals(RelativeTime.Yesterday("23:20"), RelativeTimes.of(now - 8 * 3_600_000L - 40 * 60_000L, now, tz))
        assertIs<RelativeTime.Absolute>(RelativeTimes.of(now - 3 * 86_400_000L, now, tz))
    }
}
