package br.com.conexus.antispam.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.conexus.antispam.R
import br.com.conexus.antispam.domain.ReportCategory
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDialog
import br.com.conexus.antispam.ui.w10m.W10MDialogButton
import br.com.conexus.antispam.ui.w10m.W10MDialogButtonStyle
import br.com.conexus.antispam.ui.w10m.W10MNotice
import br.com.conexus.antispam.ui.w10m.W10MNoticeTone

/**
 * O W10MButton desenha dentro de um padding vertical de 4dp: 56dp por fora dá 48dp de área tocável.
 */
internal fun Modifier.touchTarget(): Modifier = heightIn(min = 56.dp)

internal fun NoticeKind.messageRes(): Int = when (this) {
    NoticeKind.BLOCKED -> R.string.hist_notice_blocked
    NoticeKind.ALLOWED -> R.string.hist_notice_allowed
    NoticeKind.UNBLOCKED -> R.string.hist_notice_unblocked
    NoticeKind.REPORTED -> R.string.hist_notice_reported
    NoticeKind.ALREADY_BLOCKED -> R.string.hist_notice_already_blocked
    NoticeKind.ALREADY_ALLOWED -> R.string.hist_notice_already_allowed
    NoticeKind.ALREADY_REPORTED -> R.string.hist_notice_already_reported
    NoticeKind.INVALID -> R.string.hist_notice_invalid
    NoticeKind.EMERGENCY -> R.string.hist_notice_emergency
    NoticeKind.HIDDEN -> R.string.hist_notice_hidden
    NoticeKind.DELETED -> R.string.hist_notice_deleted
    NoticeKind.CLEARED -> R.string.hist_notice_cleared
    NoticeKind.UNDONE -> R.string.hist_notice_undone
}

internal fun NoticeKind.isError(): Boolean = when (this) {
    NoticeKind.INVALID, NoticeKind.EMERGENCY, NoticeKind.HIDDEN -> true
    else -> false
}

/** Aviso de resultado de ação, com "Desfazer" quando houver como desfazer. Anunciado por leitores de tela. */
@Composable
fun NoticeBar(
    notice: Notice,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        W10MNotice(
            message = stringResource(notice.kind.messageRes()),
            tone = when {
                notice.kind.isError() -> W10MNoticeTone.Warning
                notice.kind == NoticeKind.ALREADY_BLOCKED || notice.kind == NoticeKind.ALREADY_ALLOWED ||
                    notice.kind == NoticeKind.ALREADY_REPORTED -> W10MNoticeTone.Info
                else -> W10MNoticeTone.Success
            },
            actionLabel = if (notice.undo != null) stringResource(R.string.hist_notice_undo) else null,
            onActionClick = onUndo,
        )
        W10MButton(
            label = stringResource(R.string.hist_notice_dismiss),
            variant = W10MButtonVariant.Text,
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = W10MSpacing.gutter).touchTarget(),
        )
    }
}

/** Escolha da categoria da denúncia (Telemarketing, Golpe, Cobrança, Outro). */
@Composable
fun ReportCategoryDialog(
    visible: Boolean,
    onSelect: (ReportCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    W10MDialog(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(R.string.hist_report_title),
        message = stringResource(R.string.hist_report_message),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(W10MSpacing.s)) {
                ReportCategory.values().forEach { category ->
                    W10MButton(
                        label = category.label,
                        variant = W10MButtonVariant.Secondary,
                        block = true,
                        onClick = { onSelect(category) },
                        modifier = Modifier.touchTarget(),
                    )
                }
            }
        },
        buttons = listOf(
            W10MDialogButton(stringResource(R.string.hist_cancel), onClick = {}, style = W10MDialogButtonStyle.Cancel),
        ),
    )
}
