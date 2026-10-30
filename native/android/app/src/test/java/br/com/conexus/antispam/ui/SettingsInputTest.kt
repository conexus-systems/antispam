package br.com.conexus.antispam.ui

import br.com.conexus.antispam.domain.RuleKind
import br.com.conexus.antispam.ui.rules.sanitizeValue
import br.com.conexus.antispam.ui.settings.AccentColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsInputTest {
    @Test fun dddKeepsOnlyTwoDigits() {
        assertEquals("21", sanitizeValue(RuleKind.DDD, "2a1x9"))
    }

    @Test fun prefixKeepsDigitsAndLeadingPlusOnly() {
        assertEquals("+55 11 4000", sanitizeValue(RuleKind.PREFIX, "+55 11 4000abc"))
        assertEquals("55114000", sanitizeValue(RuleKind.PREFIX, "55+11+4000"))
    }

    @Test fun accentAcceptsHexAndNormalizes() {
        assertEquals("#1BA1E2", AccentColor.normalize("1ba1e2"))
        assertEquals("#1BA1E2", AccentColor.normalize(" #1BA1E2 "))
        assertNull(AccentColor.normalize("#12"))
        assertNull(AccentColor.normalize("#GGGGGG"))
    }
}
