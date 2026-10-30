package br.com.conexus.antispam.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.domain.ListItem
import br.com.conexus.antispam.domain.ListKind
import br.com.conexus.antispam.ui.settings.MinTouch
import br.com.conexus.antispam.ui.settings.SubScreenHeader
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MIconButton
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MNotice
import br.com.conexus.antispam.ui.w10m.W10MNoticeTone
import br.com.conexus.antispam.ui.w10m.W10MPivot
import br.com.conexus.antispam.ui.w10m.W10MPivotSection
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.delay

private const val UNDO_WINDOW_MS = 6_000L

@Composable
fun ListsScreen(onBack: () -> Unit) {
    val vm: ListsViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val allowed by vm.allowed.collectAsStateWithLifecycle()

    // A barra some sozinha depois de alguns segundos.
    val fb = ui.feedback
    LaunchedEffect(fb) {
        if (fb != null) {
            delay(UNDO_WINDOW_MS)
            vm.dismissFeedback()
        }
    }

    val isBlockedTab = ui.tab == ListKind.BLOCKED
    val items = if (isBlockedTab) blocked else allowed

    W10MScreen {
        Column(Modifier.fillMaxSize()) {
            SubScreenHeader(
                title = stringResource(R.string.lists_title),
                backDescription = stringResource(R.string.lists_back),
                onBack = onBack,
            )
            W10MPivot(
                sections = listOf(
                    W10MPivotSection("blocked", stringResource(R.string.lists_tab_blocked)),
                    W10MPivotSection("allowed", stringResource(R.string.lists_tab_allowed)),
                ),
                selectedKey = if (isBlockedTab) "blocked" else "allowed",
                onSelect = { vm.selectTab(if (it == "blocked") ListKind.BLOCKED else ListKind.ALLOWED) },
            )

            AddForm(
                number = ui.number,
                note = ui.note,
                isBlockedTab = isBlockedTab,
                onNumber = vm::setNumber,
                onNote = vm::setNote,
                onAdd = vm::addTyped,
            )

            if (fb != null) {
                val text = feedbackText(fb)
                W10MNotice(
                    message = text,
                    tone = if (fb.kind.isError) W10MNoticeTone.Danger else W10MNoticeTone.Success,
                    actionLabel = if (fb.undo != null) stringResource(R.string.lists_undo) else null,
                    onActionClick = vm::undo,
                )
            }

            when {
                items == null -> Unit
                items.isEmpty() -> W10MText(
                    text = stringResource(if (isBlockedTab) R.string.lists_empty_blocked else R.string.lists_empty_allowed),
                    variant = W10MType::body,
                    tone = W10MTextTone.Muted,
                    modifier = Modifier.padding(W10MSpacing.gutter),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(items, key = { it.number }) { item ->
                        NumberRow(
                            item = item,
                            isBlockedTab = isBlockedTab,
                            onMove = { vm.moveToOther(item, ui.tab) },
                            onRemove = { vm.remove(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun feedbackText(fb: ListFeedback): String = when (fb.kind) {
    ListFeedbackKind.INVALID -> stringResource(R.string.lists_msg_invalid)
    ListFeedbackKind.DUPLICATE -> stringResource(R.string.lists_msg_duplicate)
    ListFeedbackKind.EMERGENCY -> stringResource(R.string.lists_msg_emergency)
    ListFeedbackKind.BLOCKED -> stringResource(R.string.lists_msg_blocked, fb.display)
    ListFeedbackKind.ALLOWED -> stringResource(R.string.lists_msg_allowed, fb.display)
    ListFeedbackKind.REMOVED -> stringResource(R.string.lists_msg_removed, fb.display)
}

@Composable
private fun AddForm(
    number: String,
    note: String,
    isBlockedTab: Boolean,
    onNumber: (String) -> Unit,
    onNote: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.padding(horizontal = W10MSpacing.gutter), verticalArrangement = Arrangement.spacedBy(W10MSpacing.s)) {
        W10MTextField(
            label = stringResource(R.string.lists_input_label),
            value = number,
            onValueChange = onNumber,
            placeholder = stringResource(R.string.lists_input_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        W10MTextField(
            label = stringResource(R.string.lists_note_label),
            value = note,
            onValueChange = onNote,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        W10MButton(
            label = stringResource(if (isBlockedTab) R.string.lists_add_block else R.string.lists_add_allow),
            variant = W10MButtonVariant.Primary,
            enabled = number.isNotBlank(),
            onClick = onAdd,
            block = true,
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
}

@Composable
private fun NumberRow(item: ListItem, isBlockedTab: Boolean, onMove: () -> Unit, onRemove: () -> Unit) {
    val palette = useW10MTheme().palette
    val sourceTag = when (item.source) {
        "report" -> stringResource(R.string.lists_source_report)
        "remote" -> stringResource(R.string.lists_source_remote)
        else -> null
    }
    val subtitle = listOfNotNull(item.note?.takeIf { it.isNotBlank() }, sourceTag).joinToString(" · ").ifEmpty { null }
    W10MListItem(
        title = item.display,
        subtitle = subtitle,
        modifier = Modifier.heightIn(min = MinTouch),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                W10MButton(
                    label = stringResource(if (isBlockedTab) R.string.lists_unblock else R.string.lists_block_instead),
                    size = W10MButtonSize.Small,
                    variant = W10MButtonVariant.Secondary,
                    onClick = onMove,
                    modifier = Modifier.heightIn(min = MinTouch),
                    contentDescription = stringResource(
                        if (isBlockedTab) R.string.lists_unblock_desc else R.string.lists_block_instead_desc,
                        item.display,
                    ),
                )
                W10MIconButton(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = palette.danger) },
                    onClick = onRemove,
                    contentDescription = stringResource(R.string.lists_remove, item.display),
                    modifier = Modifier.size(MinTouch),
                )
            }
        },
    )
}
