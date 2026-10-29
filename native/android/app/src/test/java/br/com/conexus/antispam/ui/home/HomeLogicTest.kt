package br.com.conexus.antispam.ui.home

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.HistoryItem
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLogicTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(h: Int, d: Int = 28) =
        ZonedDateTime.of(2026, 10, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun item(atMillis: Long, action: CallAction, simulated: Boolean = false) = HistoryItem(
        id = atMillis.toString() + action.name, number = "+5511912345678", display = "+55 (11) 91234-5678",
        name = null, atMillis = atMillis, action = action, score = 0, confidence = Confidence.LOW,
        reasons = emptyList(), explanation = emptyList(), category = null, reported = false, simulated = simulated,
    )

    @Test fun `protecao inativa quando o app nao tem o papel`() {
        assertEquals(ProtectionState.INACTIVE, protectionState(false, ProtectionMode.BALANCED))
        assertEquals(ProtectionState.INACTIVE, protectionState(false, ProtectionMode.OFF))
    }

    @Test fun `protecao ativa so com papel e modo ligado`() {
        assertEquals(ProtectionState.ACTIVE, protectionState(true, ProtectionMode.BALANCED))
        assertEquals(ProtectionState.PAUSED, protectionState(true, ProtectionMode.OFF))
    }

    @Test fun `modo desconhecido volta ao equilibrado`() {
        assertEquals(ProtectionMode.BALANCED, parseMode("lixo"))
        assertEquals(ProtectionMode.AGGRESSIVE, parseMode("AGGRESSIVE"))
    }

    @Test fun `contadores do dia ignoram dias anteriores e simuladas`() {
        val start = startOfDayMillis(at(15), zone)
        val items = listOf(
            item(at(14), CallAction.BLOCK),
            item(at(9), CallAction.BLOCK),
            item(at(10), CallAction.ALLOW),
            item(at(11), CallAction.WARN, simulated = true),
            item(at(23, d = 27), CallAction.SILENCE), // ontem
        )
        val c = countsSince(items, start)
        assertEquals(2, c.blocked)
        assertEquals(0, c.silenced)
        assertEquals(0, c.warned)
        assertEquals(1, c.allowed)
        assertEquals(3, c.total)
    }

    @Test fun `inicio do dia e meia-noite local`() {
        assertEquals(at(0), startOfDayMillis(at(17), zone))
    }

    @Test fun `hora hoje e data com hora em outro dia`() {
        assertEquals("14:00", formatWhen(at(14), at(20), zone))
        assertEquals("27/10 14:00", formatWhen(at(14, d = 27), at(20), zone))
    }
}
