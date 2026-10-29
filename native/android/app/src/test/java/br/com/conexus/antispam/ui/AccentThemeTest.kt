package br.com.conexus.antispam.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentThemeTest {
    @Test fun `tema respeita a escolha e segue o sistema`() {
        assertTrue(resolveDark("dark", systemDark = false))
        assertFalse(resolveDark("light", systemDark = true))
        assertTrue(resolveDark("system", systemDark = true))
        assertFalse(resolveDark(null, systemDark = false))
    }

    @Test fun `padrao antigo azul vira a cor da marca`() {
        assertEquals("#DC2626", resolveAccent("#1BA1E2"))
        assertEquals("#DC2626", resolveAccent(null))
    }
}
