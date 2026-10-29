package br.com.conexus.antispam.ui.home

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.HistoryCounts
import br.com.conexus.antispam.domain.HistoryItem
import java.time.Instant
import java.time.ZoneId

/** Estado da proteção mostrado na Home. */
enum class ProtectionState {
    /** Papel de triagem ativo e modo diferente de Desligado. */
    ACTIVE,
    /** Papel ativo, mas o modo é Desligado. */
    PAUSED,
    /** O app não é o app de triagem (recusou, perdeu o papel ou o aparelho não oferece). */
    INACTIVE,
}

fun protectionState(roleHeld: Boolean, mode: ProtectionMode): ProtectionState = when {
    !roleHeld -> ProtectionState.INACTIVE
    mode == ProtectionMode.OFF -> ProtectionState.PAUSED
    else -> ProtectionState.ACTIVE
}

/** Valor salvo em UserSettings.mode -> enum (qualquer lixo vira o padrão Equilibrado). */
fun parseMode(raw: String): ProtectionMode =
    ProtectionMode.entries.firstOrNull { it.name == raw } ?: ProtectionMode.BALANCED

/** Início do dia local de [nowMillis], em epoch millis. */
fun startOfDayMillis(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/** Contadores do dia: só chamadas reais (as simuladas nunca são gravadas, mas o filtro protege). */
fun countsSince(items: List<HistoryItem>, sinceMillis: Long): HistoryCounts {
    val today = items.filter { it.atMillis >= sinceMillis && !it.simulated }
    fun n(a: CallAction) = today.count { it.action == a }
    return HistoryCounts(n(CallAction.BLOCK), n(CallAction.SILENCE), n(CallAction.WARN), n(CallAction.ALLOW))
}

/** "14:32" para hoje, "27/10 14:32" para outros dias. */
fun formatWhen(atMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(zone)
    val sameDay = at.toLocalDate() == Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
    return java.time.format.DateTimeFormatter.ofPattern(pattern).format(at)
}
