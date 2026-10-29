package br.com.conexus.antispam.ui.history

import br.com.conexus.antispam.decision.NumberKind
import br.com.conexus.antispam.decision.PhoneNormalizer
import br.com.conexus.antispam.domain.ListRepository
import br.com.conexus.antispam.domain.ListResult
import br.com.conexus.antispam.domain.ReportCategory
import br.com.conexus.antispam.domain.ReportReceipt
import br.com.conexus.antispam.domain.ReportRepository
import br.com.conexus.antispam.domain.ReportResult

/** Mensagens curtas depois de uma ação; a UI traduz cada tipo para uma string. */
enum class NoticeKind {
    BLOCKED, ALLOWED, UNBLOCKED, REPORTED,
    ALREADY_BLOCKED, ALREADY_ALLOWED, ALREADY_REPORTED,
    INVALID, EMERGENCY, HIDDEN,
    DELETED, CLEARED, UNDONE,
}

/** Como desfazer uma ação já concluída. */
sealed class UndoAction {
    data class List(val added: ListResult.Added) : UndoAction()
    data class Report(val receipt: ReportReceipt) : UndoAction()
}

data class Notice(val kind: NoticeKind, val undo: UndoAction? = null)

/**
 * Bloquear, permitir, denunciar e desfazer, compartilhado por Histórico e Verificar. Só fala com os
 * repositórios de domínio; devolve um [Notice] para a UI mostrar (com "Desfazer" quando houver).
 */
class ListActions(
    private val lists: ListRepository,
    private val reports: ReportRepository,
) {
    /** Número oculto/sem identificação nunca entra em listas (inválido e emergência são tratados pelos repositórios). */
    fun isActionable(number: String): Boolean =
        number.isNotBlank() && PhoneNormalizer.normalize(number).kind != NumberKind.HIDDEN

    suspend fun block(number: String): Notice {
        if (!isActionable(number)) return Notice(NoticeKind.HIDDEN)
        return when (val r = lists.block(number)) {
            is ListResult.Added -> Notice(NoticeKind.BLOCKED, UndoAction.List(r))
            ListResult.Duplicate -> Notice(NoticeKind.ALREADY_BLOCKED)
            ListResult.Invalid -> Notice(NoticeKind.INVALID)
            ListResult.Emergency -> Notice(NoticeKind.EMERGENCY)
        }
    }

    /** "Desbloquear" e "Permitir sempre": ambos movem o número para a lista de permitidos. */
    suspend fun allow(number: String, wasBlocked: Boolean = false): Notice {
        if (!isActionable(number)) return Notice(NoticeKind.HIDDEN)
        return when (val r = lists.allow(number)) {
            is ListResult.Added ->
                Notice(if (wasBlocked) NoticeKind.UNBLOCKED else NoticeKind.ALLOWED, UndoAction.List(r))
            ListResult.Duplicate -> Notice(NoticeKind.ALREADY_ALLOWED)
            ListResult.Invalid -> Notice(NoticeKind.INVALID)
            ListResult.Emergency -> Notice(NoticeKind.EMERGENCY)
        }
    }

    suspend fun report(number: String, category: ReportCategory): Notice {
        if (!isActionable(number)) return Notice(NoticeKind.HIDDEN)
        return when (val r = reports.report(number, category)) {
            is ReportResult.Saved -> Notice(NoticeKind.REPORTED, UndoAction.Report(r.receipt))
            is ReportResult.AlreadyReported -> Notice(NoticeKind.ALREADY_REPORTED)
            ReportResult.Invalid -> Notice(NoticeKind.INVALID)
            ReportResult.Emergency -> Notice(NoticeKind.EMERGENCY)
        }
    }

    suspend fun undo(action: UndoAction): Notice {
        when (action) {
            is UndoAction.List -> lists.undo(action.added)
            is UndoAction.Report -> reports.undo(action.receipt)
        }
        return Notice(NoticeKind.UNDONE)
    }
}
