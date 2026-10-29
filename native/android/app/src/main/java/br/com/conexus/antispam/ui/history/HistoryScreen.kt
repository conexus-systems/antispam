package br.com.conexus.antispam.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ads.AdBanner
import br.com.conexus.antispam.domain.HistoryItem
import br.com.conexus.antispam.domain.ListStatus
import br.com.conexus.antispam.domain.label
import br.com.conexus.antispam.domain.pastLabel
import br.com.conexus.antispam.domain.riskBandLabel
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDialog
import br.com.conexus.antispam.ui.w10m.W10MDialogButton
import br.com.conexus.antispam.ui.w10m.W10MDialogButtonStyle
import br.com.conexus.antispam.ui.w10m.W10MDivider
import br.com.conexus.antispam.ui.w10m.W10MEmpty
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MLoading
import br.com.conexus.antispam.ui.w10m.W10MScore
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MTag
import br.com.conexus.antispam.ui.w10m.W10MTagTone
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType

/** Histórico real: lista filtrável, detalhe com a explicação da decisão e ações (RF-HIST, RF-LIST, RF-DENUN). */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val reporting by viewModel.reporting.collectAsStateWithLifecycle()
    val inDetail = detail != null

    BackHandler(enabled = inDetail) { viewModel.closeDetail() }

    W10MScreen {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val d = detail
                if (d != null) {
                    HistoryDetail(
                        state = d,
                        nowMillis = viewModel.nowMillis(),
                        onBack = viewModel::closeDetail,
                        onBlock = { viewModel.block(it) },
                        onAllow = { item, wasBlocked -> viewModel.allow(item, wasBlocked) },
                        onReport = viewModel::askReport,
                        onDelete = viewModel::askDelete,
                    )
                } else {
                    HistoryList(
                        state = list,
                        nowMillis = viewModel.nowMillis(),
                        onTab = viewModel::selectTab,
                        onOpen = { viewModel.open(it.id) },
                        onClear = viewModel::askClear,
                    )
                }
            }
            notice?.let {
                NoticeBar(notice = it, onUndo = viewModel::undo, onDismiss = viewModel::dismissNotice)
            }
            // A2: banner só no rodapé da lista, fora da área de toque dela, depois de carregar, e
            // nunca no detalhe, em diálogo de confirmação ou durante ação destrutiva.
            if (!inDetail && list.items != null && confirm == null && !reporting) {
                AdBanner(viewModel.adsManager)
            }
        }

        val item = detail?.item
        ReportCategoryDialog(
            visible = reporting && item != null,
            onSelect = { category -> item?.let { viewModel.report(it, category) } },
            onDismiss = viewModel::cancelReport,
        )
        W10MDialog(
            visible = confirm != null,
            onDismiss = viewModel::cancelConfirm,
            title = stringResource(
                if (confirm == HistoryConfirm.CLEAR) R.string.hist_clear_title else R.string.hist_delete_title,
            ),
            message = stringResource(
                if (confirm == HistoryConfirm.CLEAR) R.string.hist_clear_message else R.string.hist_delete_message,
            ),
            buttons = listOf(
                W10MDialogButton(stringResource(R.string.hist_cancel), onClick = {}, style = W10MDialogButtonStyle.Cancel),
                W10MDialogButton(
                    stringResource(
                        if (confirm == HistoryConfirm.CLEAR) R.string.hist_clear_confirm else R.string.hist_delete_confirm,
                    ),
                    onClick = viewModel::confirmDestructive,
                    style = W10MDialogButtonStyle.Destructive,
                ),
            ),
        )
    }
}

@Composable
private fun HistoryList(
    state: HistoryListState,
    nowMillis: Long,
    onTab: (HistoryTab) -> Unit,
    onOpen: (HistoryItem) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        W10MText(
            text = stringResource(R.string.history_title),
            variant = W10MType::header,
            modifier = Modifier
                .padding(start = W10MSpacing.gutter, top = W10MSpacing.l, end = W10MSpacing.gutter)
                .semantics { heading() },
        )
        FilterRow(selected = state.tab, onSelect = onTab)
        val items = state.items
        when {
            items == null -> W10MLoading(message = stringResource(R.string.hist_loading), modifier = Modifier.weight(1f))
            items.isEmpty() -> W10MEmpty(
                message = stringResource(
                    if (state.tab == HistoryTab.ALL) R.string.hist_empty_all else R.string.hist_empty_filtered,
                ),
                modifier = Modifier.weight(1f),
            )
            else -> {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(items, key = { it.id }) { item -> HistoryRow(item, nowMillis, onClick = { onOpen(item) }) }
                }
                W10MButton(
                    label = stringResource(R.string.hist_clear_action),
                    variant = W10MButtonVariant.Text,
                    icon = { W10MIcon(name = W10MIcons.Delete, color = useW10MTheme().palette.danger) },
                    onClick = onClear,
                    modifier = Modifier.padding(horizontal = W10MSpacing.gutter).touchTarget(),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(selected: HistoryTab, onSelect: (HistoryTab) -> Unit) {
    val palette = useW10MTheme().palette
    val desc = stringResource(R.string.hist_filters_desc)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = W10MSpacing.gutter)
            .semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(W10MSpacing.l),
    ) {
        HistoryTab.values().forEach { tab ->
            val label = stringResource(tab.labelRes())
            val isSelected = tab == selected
            Column(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(tab) }
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                        contentDescription = label
                    },
                verticalArrangement = Arrangement.Center,
            ) {
                W10MText(
                    text = label,
                    variant = W10MType::body,
                    tone = if (isSelected) W10MTextTone.Text else W10MTextTone.Muted,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (isSelected) palette.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

private fun HistoryTab.labelRes(): Int = when (this) {
    HistoryTab.ALL -> R.string.hist_filter_all
    HistoryTab.BLOCKED -> R.string.hist_filter_blocked
    HistoryTab.SILENCED -> R.string.hist_filter_silenced
    HistoryTab.ALLOWED -> R.string.hist_filter_allowed
    HistoryTab.REPORTED -> R.string.hist_filter_reported
}

@Composable
private fun relativeText(at: Long, now: Long): String = when (val t = RelativeTimes.of(at, now)) {
    RelativeTime.Now -> stringResource(R.string.hist_time_now)
    is RelativeTime.MinutesAgo -> stringResource(R.string.hist_time_minutes, t.minutes)
    is RelativeTime.HoursAgo -> stringResource(R.string.hist_time_hours, t.hours)
    is RelativeTime.Yesterday -> stringResource(R.string.hist_time_yesterday, t.time)
    is RelativeTime.Absolute -> t.text
}

@Composable
private fun HistoryItem.title(): String =
    name?.takeIf { it.isNotBlank() } ?: display.takeIf { it.isNotBlank() } ?: stringResource(R.string.hist_hidden_number)

@Composable
private fun HistoryRow(item: HistoryItem, nowMillis: Long, onClick: () -> Unit) {
    val palette = useW10MTheme().palette
    val title = item.title()
    val whenText = relativeText(item.atMillis, nowMillis)
    val shown = item.reasons.take(2).map { it.label() }
    val extra = item.reasons.size - shown.size
    val reasonText = when {
        shown.isEmpty() -> stringResource(R.string.hist_no_reason)
        extra > 0 -> shown.joinToString(", ") + " " + stringResource(R.string.hist_reason_more, extra)
        else -> shown.joinToString(", ")
    }
    val subtitle = buildString {
        append(whenText).append(" · ").append(reasonText)
        if (item.reported) append(" · ").append(stringResource(R.string.hist_reported_tag))
        if (item.simulated) append(" · ").append(stringResource(R.string.hist_simulated))
    }
    val rowDesc = stringResource(R.string.hist_item_desc, title, item.action.pastLabel(), subtitle)
    W10MListItem(
        title = title,
        subtitle = subtitle,
        leading = {
            W10MIcon(
                name = when (item.action) {
                    br.com.conexus.antispam.decision.CallAction.BLOCK -> W10MIcons.Block
                    br.com.conexus.antispam.decision.CallAction.WARN -> W10MIcons.Warning
                    br.com.conexus.antispam.decision.CallAction.SILENCE -> W10MIcons.PhoneOff
                    br.com.conexus.antispam.decision.CallAction.ALLOW -> W10MIcons.Check
                },
                color = palette.actionColor(item.action.name),
            )
        },
        meta = item.action.pastLabel(),
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = rowDesc; role = Role.Button },
    )
}

@Composable
private fun HistoryDetail(
    state: HistoryDetailState,
    nowMillis: Long,
    onBack: () -> Unit,
    onBlock: (HistoryItem) -> Unit,
    onAllow: (HistoryItem, Boolean) -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = useW10MTheme().palette
    val item = state.item
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        W10MButton(
            label = stringResource(R.string.hist_detail_back),
            variant = W10MButtonVariant.Text,
            icon = { W10MIcon(name = W10MIcons.Close) },
            onClick = onBack,
            modifier = Modifier.padding(horizontal = W10MSpacing.gutter).touchTarget(),
        )
        if (item == null) {
            W10MText(
                text = stringResource(R.string.hist_detail_missing),
                modifier = Modifier.padding(W10MSpacing.gutter),
                tone = W10MTextTone.Muted,
            )
            return
        }
        val gutter = Modifier.padding(horizontal = W10MSpacing.gutter)
        W10MText(
            text = item.title(),
            variant = W10MType::header,
            modifier = gutter.semantics { heading() },
        )
        W10MText(
            text = stringResource(R.string.hist_detail_action) + ": " + item.action.pastLabel() +
                " · " + relativeText(item.atMillis, nowMillis),
            variant = W10MType::subtitle,
            tone = when (item.action) {
                br.com.conexus.antispam.decision.CallAction.BLOCK -> W10MTextTone.Danger
                br.com.conexus.antispam.decision.CallAction.WARN -> W10MTextTone.Warn
                else -> W10MTextTone.Text
            },
            modifier = gutter,
        )
        Row(gutter.padding(top = W10MSpacing.s), horizontalArrangement = Arrangement.spacedBy(W10MSpacing.s)) {
            if (item.reported) W10MTag(text = stringResource(R.string.hist_reported_tag), tone = W10MTagTone.Danger)
            if (item.simulated) W10MTag(text = stringResource(R.string.hist_simulated), tone = W10MTagTone.Info)
        }
        item.category?.takeIf { it.isNotBlank() }?.let {
            W10MText(
                text = stringResource(R.string.hist_detail_category, it),
                variant = W10MType::caption,
                tone = W10MTextTone.Muted,
                modifier = gutter.padding(top = W10MSpacing.xs),
            )
        }
        Spacer(Modifier.height(W10MSpacing.l))

        W10MText(
            text = stringResource(R.string.hist_detail_score),
            variant = W10MType::group,
            tone = W10MTextTone.Accent,
            modifier = gutter,
        )
        val scoreDesc = stringResource(R.string.ver_result_score_desc, item.score)
        W10MScore(item.score, Modifier.semantics(mergeDescendants = true) { contentDescription = scoreDesc })
        W10MText(
            text = stringResource(R.string.hist_detail_risk, riskBandLabel(item.score)) + " · " +
                stringResource(R.string.hist_detail_confidence, item.confidence.label()),
            variant = W10MType::caption,
            tone = W10MTextTone.Muted,
            modifier = gutter.padding(top = W10MSpacing.xs),
        )
        Spacer(Modifier.height(W10MSpacing.l))

        W10MText(
            text = stringResource(R.string.hist_detail_why),
            variant = W10MType::group,
            tone = W10MTextTone.Accent,
            modifier = gutter.semantics { heading() },
        )
        val reasons = item.explanation.ifEmpty { listOf(stringResource(R.string.hist_no_reason)) }
        reasons.forEach { line ->
            W10MText(text = "• $line", modifier = gutter.padding(top = W10MSpacing.xs))
        }
        if (item.reasons.isNotEmpty()) {
            Spacer(Modifier.height(W10MSpacing.m))
            W10MText(
                text = stringResource(R.string.hist_detail_rules),
                variant = W10MType::group,
                tone = W10MTextTone.Accent,
                modifier = gutter,
            )
            item.reasons.forEach { r ->
                W10MText(text = "• " + r.label(), tone = W10MTextTone.Muted, modifier = gutter.padding(top = W10MSpacing.xs))
            }
        }
        Spacer(Modifier.height(W10MSpacing.m))
        W10MDivider()
        Spacer(Modifier.height(W10MSpacing.m))

        when (state.listStatus) {
            ListStatus.BLOCKED -> W10MText(
                text = stringResource(R.string.hist_detail_list_blocked), tone = W10MTextTone.Muted, modifier = gutter,
            )
            ListStatus.ALLOWED -> W10MText(
                text = stringResource(R.string.hist_detail_list_allowed), tone = W10MTextTone.Muted, modifier = gutter,
            )
            ListStatus.NONE -> Unit
        }
        if (!state.actionable) {
            W10MText(
                text = stringResource(R.string.hist_detail_hidden_note), tone = W10MTextTone.Muted, modifier = gutter,
            )
        } else {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(W10MSpacing.xs)) {
                if (state.listStatus == ListStatus.BLOCKED) {
                    W10MButton(
                        label = stringResource(R.string.hist_act_unblock),
                        variant = W10MButtonVariant.Primary, block = true,
                        icon = { W10MIcon(name = W10MIcons.Check, color = palette.onAccent) },
                        onClick = { onAllow(item, true) }, modifier = Modifier.touchTarget(),
                    )
                } else {
                    W10MButton(
                        label = stringResource(R.string.hist_act_block),
                        variant = W10MButtonVariant.Primary, block = true,
                        icon = { W10MIcon(name = W10MIcons.Block, color = palette.onAccent) },
                        onClick = { onBlock(item) }, modifier = Modifier.touchTarget(),
                    )
                    if (state.listStatus == ListStatus.NONE) {
                        W10MButton(
                            label = stringResource(R.string.hist_act_allow),
                            variant = W10MButtonVariant.Secondary, block = true,
                            icon = { W10MIcon(name = W10MIcons.Check, color = palette.text) },
                            onClick = { onAllow(item, false) }, modifier = Modifier.touchTarget(),
                        )
                    }
                }
                if (!item.reported) {
                    W10MButton(
                        label = stringResource(R.string.hist_act_report),
                        variant = W10MButtonVariant.Danger, block = true,
                        icon = { W10MIcon(name = W10MIcons.Flag, color = palette.onAccent) },
                        onClick = onReport, modifier = Modifier.touchTarget(),
                    )
                }
            }
        }
        W10MButton(
            label = stringResource(R.string.hist_delete_action),
            variant = W10MButtonVariant.Text,
            icon = { W10MIcon(name = W10MIcons.Delete, color = palette.danger) },
            onClick = onDelete,
            modifier = gutter.padding(top = W10MSpacing.s).touchTarget(),
        )
        Spacer(Modifier.height(W10MSpacing.xl))
    }
}
