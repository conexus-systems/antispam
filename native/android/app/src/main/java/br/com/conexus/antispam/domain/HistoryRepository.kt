package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.HistoryEntry
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.DecisionReason
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Histórico de decisões (RF-HIST-01/02). Todas as telas leem daqui. */
@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val clock: Clock,
) {

    /** Mais recentes primeiro. */
    fun observe(filter: HistoryFilter = HistoryFilter.ALL, limit: Int = 500): Flow<List<HistoryItem>> {
        val flow = when (filter) {
            HistoryFilter.ALL -> dao.observeRecent(limit)
            HistoryFilter.BLOCKED -> dao.observeByAction(CallAction.BLOCK.name, limit)
            HistoryFilter.SILENCED -> dao.observeByAction(CallAction.SILENCE.name, limit)
            HistoryFilter.WARNED -> dao.observeByAction(CallAction.WARN.name, limit)
            HistoryFilter.ALLOWED -> dao.observeByAction(CallAction.ALLOW.name, limit)
        }
        return flow.map { list -> list.map(::toItem) }
    }

    /** Detalhe de uma entrada (null se apagada). */
    fun observeItem(id: String): Flow<HistoryItem?> = dao.observe(id).map { it?.let(::toItem) }

    suspend fun get(id: String): HistoryItem? = dao.get(id)?.let(::toItem)

    /** Totais para a Home (chamadas reais; simuladas ficam de fora). */
    fun observeCounts(): Flow<HistoryCounts> = dao.observeActionCounts().map { rows ->
        fun n(a: CallAction) = rows.firstOrNull { it.action == a.name }?.count ?: 0
        HistoryCounts(n(CallAction.BLOCK), n(CallAction.SILENCE), n(CallAction.WARN), n(CallAction.ALLOW))
    }

    /** Grava a decisão completa (score, confiança, motivos, explicação). Devolve o item gravado. */
    suspend fun record(
        decision: CallDecision,
        atMillis: Long = clock.nowMillis(),
        name: String? = null,
        simulated: Boolean = false,
    ): HistoryItem {
        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            number = PhoneFormat.key(decision.normalized),
            display = PhoneFormat.display(decision.normalized),
            name = name,
            at = IsoTime.format(atMillis),
            action = decision.action.name,
            score = decision.score,
            confidence = decision.confidence.name,
            reasons = JSON.encodeToString(STRINGS, decision.reasons.map { it.name }),
            explanation = JSON.encodeToString(STRINGS, decision.explanation),
            simulated = simulated,
        )
        dao.insert(entry)
        return toItem(entry)
    }

    suspend fun delete(id: String) { dao.delete(id) }

    /** "Apagar histórico". */
    suspend fun clear() { dao.clear() }

    /** Retenção (historyRetentionDays). Devolve quantas linhas foram apagadas. */
    suspend fun purgeExpired(retentionDays: Int): Int {
        if (retentionDays <= 0) return 0
        val cutoff = clock.nowMillis() - retentionDays * 24L * 60L * 60L * 1000L
        return dao.purgeBefore(IsoTime.format(cutoff))
    }

    private fun toItem(e: HistoryEntry): HistoryItem = HistoryItem(
        id = e.id,
        number = e.number,
        display = e.display,
        name = e.name,
        atMillis = IsoTime.parse(e.at),
        action = enumOr(e.action, CallAction.ALLOW),
        score = e.score,
        confidence = enumOr(e.confidence, Confidence.LOW),
        reasons = decodeList(e.reasons).mapNotNull { runCatching { DecisionReason.valueOf(it) }.getOrNull() },
        explanation = decodeList(e.explanation),
        category = e.category,
        reported = e.reported,
        simulated = e.simulated,
    )

    private fun decodeList(json: String): List<String> =
        runCatching { JSON.decodeFromString(STRINGS, json) }.getOrDefault(emptyList())

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)

    companion object {
        private val JSON = Json
        private val STRINGS = ListSerializer(String.serializer())
    }
}
