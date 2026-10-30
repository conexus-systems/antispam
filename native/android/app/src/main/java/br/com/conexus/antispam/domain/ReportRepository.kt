package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.BlocklistEntry
import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.ReputationEntry
import javax.inject.Inject
import javax.inject.Singleton

sealed class ReportResult {
    /** Denúncia salva só neste aparelho. Mensagem sugerida: "Denúncia registrada neste aparelho". */
    data class Saved(val receipt: ReportReceipt) : ReportResult()
    data class AlreadyReported(val number: String) : ReportResult()
    object Invalid : ReportResult()
    object Emergency : ReportResult()
}

/** Guarda o estado anterior para [ReportRepository.undo]. */
data class ReportReceipt(
    val number: String,
    val category: ReportCategory,
    val previousListEntry: BlocklistEntry?,
    val previousReputation: ReputationEntry?,
)

/**
 * Denúncia local (RF-DENUN-01). Sem rede e sem peso mínimo do dataset: a denúncia do próprio usuário
 * vai direto para a lista bloqueada (source = "report") e cria/atualiza a reputação local.
 */
@Singleton
class ReportRepository @Inject constructor(
    private val lists: ListRepository,
    private val reputationDao: ReputationDao,
    private val historyDao: HistoryDao,
    private val clock: Clock,
) {

    suspend fun report(rawNumber: String, category: ReportCategory): ReportResult {
        val previousList = lists.get(rawNumber)
        if (previousList != null && previousList.action == ListRepository.BLOCK && previousList.source == "report") {
            return ReportResult.AlreadyReported(previousList.number)
        }
        val added = when (val r = lists.block(rawNumber, note = category.label, source = "report")) {
            is ListResult.Added -> r
            // Já bloqueado manualmente: promove para denúncia sem perder o bloqueio.
            ListResult.Duplicate -> {
                val entry = previousList!!.copy(source = "report", note = category.label)
                lists.restore(entry)
                ListResult.Added(entry, previousList)
            }
            ListResult.Invalid -> return ReportResult.Invalid
            ListResult.Emergency -> return ReportResult.Emergency
        }
        val number = added.entry.number
        val now = IsoTime.format(clock.nowMillis())
        val previousRep = reputationDao.get(number)
        val updated = previousRep?.copy(
            category = category.code,
            score = maxOf(previousRep.score, LOCAL_REPORT_SCORE),
            reports = previousRep.reports + 1,
            lastSeen = now,
        ) ?: ReputationEntry(
            key = number,
            country = "BR",
            category = category.code,
            score = LOCAL_REPORT_SCORE,
            reports = 1,
            uniqueReporters = 1,
            firstSeen = now,
            lastSeen = now,
            confidence = 0.6,
            falsePositives = 0,
            reporterIds = "[\"local\"]",
        )
        reputationDao.upsert(updated)
        historyDao.markReportedByNumber(number, category.label)
        return ReportResult.Saved(ReportReceipt(number, category, added.previous, previousRep))
    }

    /** Desfaz a denúncia: restaura lista, reputação e marca do histórico. */
    suspend fun undo(receipt: ReportReceipt) {
        if (receipt.previousListEntry != null) lists.restore(receipt.previousListEntry) else lists.remove(receipt.number)
        if (receipt.previousReputation != null) reputationDao.upsert(receipt.previousReputation) else reputationDao.remove(receipt.number)
        historyDao.clearReportedByNumber(receipt.number)
    }

    companion object {
        const val LOCAL_REPORT_SCORE = 90
    }
}
