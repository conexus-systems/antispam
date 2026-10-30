package br.com.conexus.antispam.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.HistoryCounts
import br.com.conexus.antispam.domain.HistoryItem
import br.com.conexus.antispam.domain.ScreeningResult
import br.com.conexus.antispam.domain.label
import br.com.conexus.antispam.domain.pastLabel
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.W10MType
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDivider
import br.com.conexus.antispam.ui.w10m.W10MHeader
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MNotice
import br.com.conexus.antispam.ui.w10m.W10MNoticeTone
import br.com.conexus.antispam.ui.w10m.W10MScore
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MTag
import br.com.conexus.antispam.ui.w10m.W10MTagTone
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone

private val MinTouch = 48.dp

/**
 * Início: estado real da proteção, modo, contadores do dia, últimas decisões e simulador.
 * Sem anúncios (regra A2).
 */
@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sim by viewModel.simulator.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // O papel e a permissão podem mudar fora do app (configurações do Android).
    OnResume { viewModel.refreshSystem() }
    val requestRole = rememberRoleRequester { viewModel.refreshSystem() }

    W10MScreen {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = W10MSpacing.xl),
        ) {
            W10MHeader(kicker = stringResource(R.string.hm_kicker), title = stringResource(R.string.hm_title))
            Spacer(Modifier.height(W10MSpacing.m))

            ProtectionCard(
                state = ui.protection,
                roleAvailable = ui.roleAvailable,
                onActivate = requestRole,
                onOpenSettings = onOpenSettings,
            )

            if (ui.showNotificationsWarning) {
                W10MNotice(
                    message = stringResource(R.string.hm_notif_off),
                    tone = W10MNoticeTone.Info,
                    actionLabel = stringResource(R.string.hm_notif_allow),
                    onActionClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }

            W10MListItem(
                title = stringResource(R.string.hm_mode_label),
                subtitle = stringResource(R.string.hm_mode_change_hint),
                meta = stringResource(modeLabel(ui.mode)),
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = MinTouch),
            )
            W10MDivider()

            Spacer(Modifier.height(W10MSpacing.l))
            TodayCounters(ui.today, ui.total)

            Spacer(Modifier.height(W10MSpacing.l))
            RecentDecisions(ui.recent, onSeeAll = onOpenHistory)

            Spacer(Modifier.height(W10MSpacing.l))
            Simulator(
                state = sim,
                onInput = viewModel::onSimInput,
                onSimulate = { viewModel.simulate(hidden = false) },
                onSimulateHidden = { viewModel.simulate(hidden = true) },
                onClear = viewModel::clearSimulation,
            )
        }
    }
}

private fun modeLabel(mode: ProtectionMode): Int = when (mode) {
    ProtectionMode.OFF -> R.string.hm_mode_off
    ProtectionMode.BASIC -> R.string.hm_mode_basic
    ProtectionMode.BALANCED -> R.string.hm_mode_balanced
    ProtectionMode.AGGRESSIVE -> R.string.hm_mode_aggressive
    ProtectionMode.CUSTOM -> R.string.hm_mode_custom
}

@Composable
private fun ProtectionCard(
    state: ProtectionState,
    roleAvailable: Boolean,
    onActivate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = useW10MTheme().palette
    val (title, desc, iconDesc) = when (state) {
        ProtectionState.ACTIVE -> Triple(
            stringResource(R.string.hm_protection_active),
            stringResource(R.string.hm_protection_active_desc),
            stringResource(R.string.hm_icon_shield_ok),
        )
        ProtectionState.PAUSED -> Triple(
            stringResource(R.string.hm_protection_paused),
            stringResource(R.string.hm_protection_paused_desc),
            stringResource(R.string.hm_icon_shield_off),
        )
        ProtectionState.INACTIVE -> Triple(
            stringResource(R.string.hm_protection_inactive),
            stringResource(R.string.hm_protection_inactive_desc),
            stringResource(R.string.hm_icon_shield_off),
        )
    }
    val tone = if (state == ProtectionState.ACTIVE) W10MTextTone.Accent else W10MTextTone.Warn
    val iconColor = if (state == ProtectionState.ACTIVE) palette.primary else palette.warn

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = W10MSpacing.gutter)
            .background(palette.surface)
            .padding(W10MSpacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            W10MIcon(
                name = W10MIcons.Shield,
                size = 36.dp,
                color = iconColor,
                modifier = Modifier.semantics { contentDescription = iconDesc },
            )
            Spacer(Modifier.padding(horizontal = W10MSpacing.s))
            W10MText(text = title, variant = W10MType::title, tone = tone)
        }
        Spacer(Modifier.height(W10MSpacing.s))
        W10MText(text = desc, variant = W10MType::body, tone = W10MTextTone.Muted)

        when (state) {
            ProtectionState.INACTIVE -> {
                Spacer(Modifier.height(W10MSpacing.m))
                if (roleAvailable) {
                    W10MButton(
                        label = stringResource(R.string.hm_activate),
                        onClick = onActivate,
                        variant = W10MButtonVariant.Primary,
                        block = true,
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                } else {
                    W10MText(
                        text = stringResource(R.string.hm_role_unavailable),
                        variant = W10MType::caption,
                        tone = W10MTextTone.Warn,
                    )
                }
            }
            ProtectionState.PAUSED -> {
                Spacer(Modifier.height(W10MSpacing.m))
                W10MButton(
                    label = stringResource(R.string.hm_open_settings),
                    onClick = onOpenSettings,
                    block = true,
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            }
            ProtectionState.ACTIVE -> Unit
        }
    }
}

@Composable
private fun TodayCounters(today: HistoryCounts, total: Int) {
    W10MSection(title = stringResource(R.string.hm_today)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Counter(today.blocked, stringResource(R.string.hm_blocked), Modifier.weight(1f))
            Counter(today.silenced, stringResource(R.string.hm_silenced), Modifier.weight(1f))
            Counter(today.warned, stringResource(R.string.hm_warned), Modifier.weight(1f))
            Counter(today.allowed, stringResource(R.string.hm_allowed), Modifier.weight(1f))
        }
        if (total > 0) {
            Spacer(Modifier.height(W10MSpacing.s))
            W10MText(
                text = stringResource(R.string.hm_total_since, total),
                variant = W10MType::caption,
                tone = W10MTextTone.Muted,
            )
        }
    }
}

@Composable
private fun Counter(value: Int, label: String, modifier: Modifier = Modifier) {
    val description = "$label: $value"
    Column(
        modifier = modifier
            .heightIn(min = MinTouch)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        W10MText(text = value.toString(), variant = W10MType::header, tone = W10MTextTone.Text)
        W10MText(text = label, variant = W10MType::caption, tone = W10MTextTone.Muted)
    }
}

@Composable
private fun RecentDecisions(recent: List<HistoryItem>, onSeeAll: () -> Unit) {
    W10MSection(title = stringResource(R.string.hm_recent)) {
        if (recent.isEmpty()) {
            W10MText(
                text = stringResource(R.string.hm_recent_empty),
                variant = W10MType::body,
                tone = W10MTextTone.Muted,
                modifier = Modifier.padding(vertical = W10MSpacing.s),
            )
        } else {
            val now = System.currentTimeMillis()
            recent.forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouch)
                        .clickable(onClick = onSeeAll)
                        .padding(vertical = W10MSpacing.s),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        W10MText(
                            text = item.name?.takeIf { it.isNotBlank() } ?: item.display,
                            variant = W10MType::body,
                            tone = W10MTextTone.Text,
                            modifier = Modifier.weight(1f),
                        )
                        W10MText(
                            text = formatWhen(item.atMillis, now),
                            variant = W10MType::caption,
                            tone = W10MTextTone.Muted,
                        )
                    }
                    W10MText(
                        text = item.action.pastLabel(),
                        variant = W10MType::caption,
                        tone = item.action.tone(),
                    )
                }
                W10MDivider()
            }
            W10MButton(
                label = stringResource(R.string.hm_see_all),
                onClick = onSeeAll,
                variant = W10MButtonVariant.Text,
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
    }
}

private fun CallAction.tone(): W10MTextTone = when (this) {
    CallAction.BLOCK -> W10MTextTone.Danger
    CallAction.SILENCE, CallAction.WARN -> W10MTextTone.Warn
    CallAction.ALLOW -> W10MTextTone.Accent
}

@Composable
private fun Simulator(
    state: SimulatorState,
    onInput: (String) -> Unit,
    onSimulate: () -> Unit,
    onSimulateHidden: () -> Unit,
    onClear: () -> Unit,
) {
    W10MSection(title = stringResource(R.string.hm_sim_title)) {
        W10MText(
            text = stringResource(R.string.hm_sim_desc),
            variant = W10MType::caption,
            tone = W10MTextTone.Muted,
        )
    }
    Spacer(Modifier.height(W10MSpacing.s))
    W10MTextField(
        label = stringResource(R.string.hm_sim_field),
        value = state.input,
        onValueChange = onInput,
        placeholder = stringResource(R.string.hm_sim_placeholder),
        errorText = state.error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Spacer(Modifier.height(W10MSpacing.s))
    Column(modifier = Modifier.padding(horizontal = W10MSpacing.gutter)) {
        W10MButton(
            label = stringResource(R.string.hm_sim_run),
            onClick = onSimulate,
            variant = W10MButtonVariant.Primary,
            loading = state.running,
            block = true,
            modifier = Modifier.heightIn(min = MinTouch),
        )
        Spacer(Modifier.height(W10MSpacing.xs))
        W10MButton(
            label = stringResource(R.string.hm_sim_hidden),
            onClick = onSimulateHidden,
            enabled = !state.running,
            block = true,
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
    state.result?.let { SimulationResult(it, onClear) }
}

@Composable
private fun SimulationResult(result: ScreeningResult, onClear: () -> Unit) {
    val palette = useW10MTheme().palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.m)
            .background(palette.surface)
            .padding(W10MSpacing.l),
    ) {
        W10MTag(text = stringResource(R.string.hm_sim_badge), tone = W10MTagTone.Info)
        Spacer(Modifier.height(W10MSpacing.s))
        W10MText(text = result.headline, variant = W10MType::title, tone = W10MTextTone.Text)
        W10MText(text = result.displayNumber, variant = W10MType::body, tone = W10MTextTone.Muted)
        Spacer(Modifier.height(W10MSpacing.s))
        W10MText(
            text = stringResource(R.string.hm_sim_result_action, result.decision.action.label()),
            variant = W10MType::body,
            tone = result.decision.action.tone(),
        )
        W10MText(
            text = stringResource(R.string.hm_sim_result_risk, result.riskLabel),
            variant = W10MType::body,
            tone = W10MTextTone.Muted,
        )
        Spacer(Modifier.height(W10MSpacing.s))
        Column(modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${result.decision.score} de 100"
        }) { W10MScore(score = result.decision.score) }
        Spacer(Modifier.height(W10MSpacing.s))
        result.explanation.forEach {
            W10MText(text = "• $it", variant = W10MType::caption, tone = W10MTextTone.Muted)
        }
        Spacer(Modifier.height(W10MSpacing.s))
        W10MButton(
            label = stringResource(R.string.hm_sim_clear),
            onClick = onClear,
            variant = W10MButtonVariant.Text,
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
}
