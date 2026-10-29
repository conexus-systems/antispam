package br.com.conexus.antispam.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {
    private var now = 1_000_000L
    private fun policy(state: InterstitialState = InterstitialState()) = InterstitialPolicy(state) { now }
    private fun InterstitialPolicy.verify(n: Int) = repeat(n) { recordVerification() }

    @Test fun naoMostraAntesDaTerceiraVerificacao() {
        val p = policy()
        p.verify(2)
        assertFalse(p.canShow())
        p.recordVerification()
        assertTrue(p.canShow())
    }

    @Test fun markShownZeraContadorDeVerificacoes() {
        val p = policy()
        p.verify(3); p.markShown()
        assertEquals(0, p.state.actionsSinceAd)
        p.verify(2)
        assertFalse(p.canShow())
    }

    @Test fun cooldownDeTresMinutos() {
        val p = policy()
        p.verify(3); p.markShown()
        p.verify(3)
        now += 2 * 60_000L
        assertFalse(p.canShow())
        now += 60_000L
        assertTrue(p.canShow())
    }

    @Test fun maximoTresPorHora() {
        val p = policy()
        repeat(3) {
            p.verify(3); assertTrue(p.canShow()); p.markShown(); now += 4 * 60_000L
        }
        p.verify(3)
        assertFalse(p.canShow()) // 12 min depois, 3 já exibidos na janela
    }

    @Test fun janelaDeUmaHoraExpiraELiberaDeNovo() {
        val p = policy()
        repeat(3) { p.verify(3); p.markShown(); now += 4 * 60_000L }
        p.verify(3)
        now += 3_600_000L
        assertTrue(p.canShow())
        p.markShown()
        assertEquals(1, p.state.hourCount)
    }

    @Test fun restauraEstadoPersistido() {
        val saved = InterstitialState(actionsSinceAd = 3, lastShownAt = now - 60_000L, hourWindowStart = now - 60_000L, hourCount = 1)
        val p = policy(saved)
        assertFalse(p.canShow()) // ainda em cooldown
        now += 2 * 60_000L
        assertTrue(p.canShow())
    }
}
