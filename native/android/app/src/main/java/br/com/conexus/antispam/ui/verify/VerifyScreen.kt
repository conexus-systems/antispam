package br.com.conexus.antispam.ui.verify

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.domain.ListStatus
import br.com.conexus.antispam.domain.ScreeningResult
import br.com.conexus.antispam.domain.label
import br.com.conexus.antispam.ui.history.NoticeBar
import br.com.conexus.antispam.ui.history.ReportCategoryDialog
import br.com.conexus.antispam.ui.history.touchTarget
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDivider
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MScore
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.delay

/** Pausa para o usuário ler o resultado antes de o intersticial poder aparecer (A3). */
private const val INTERSTITIAL_DELAY_MS = 2_000L

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Verificar real (RF-VER-01): mesmo motor da chamada, sem gravar nada. */
@Composable
fun VerifyScreen(viewModel: VerifyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adsState by viewModel.adsState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    // Pré-carrega ao abrir a aba e de novo quando o SDK ficar pronto (consentimento/inicialização).
    LaunchedEffect(adsState.canRequestAds, adsState.sdkReady) { viewModel.preloadAd() }

    // Intersticial só depois do resultado visível, sem diálogo aberto, e cancelado se sair da tela.
    val reporting by rememberUpdatedState(state.reporting)
    val hasError by rememberUpdatedState(state.error != null)
    LaunchedEffect(state.completedCount) {
        if (state.completedCount == 0) return@LaunchedEffect
        delay(INTERSTITIAL_DELAY_MS)
        if (!reporting && !hasError && activity != null) viewModel.onResultShown(activity)
    }

    W10MScreen {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                W10MText(
                    text = stringResource(R.string.verify_title),
                    variant = W10MType::header,
                    modifier = Modifier
                        .padding(start = W10MSpacing.gutter, top = W10MSpacing.l, end = W10MSpacing.gutter)
                        .semantics { heading() },
                )
                W10MText(
                    text = stringResource(R.string.ver_intro),
                    tone = W10MTextTone.Muted,
                    modifier = Modifier.padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.s),
                )
                W10MTextField(
                    label = stringResource(R.string.ver_field_label),
                    value = state.input,
                    onValueChange = viewModel::onInput,
                    placeholder = stringResource(R.string.ver_field_placeholder),
                    helperText = stringResource(R.string.ver_field_helper),
                    errorText = state.error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !state.loading,
                )
                state.error?.let {
                    W10MText(
                        text = it,
                        tone = W10MTextTone.Danger,
                        modifier = Modifier
                            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.xs)
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                Spacer(Modifier.height(W10MSpacing.s))
                W10MButton(
                    label = stringResource(if (state.loading) R.string.ver_checking else R.string.verify_button),
                    variant = W10MButtonVariant.Primary,
                    block = true,
                    enabled = !state.loading,
                    icon = { W10MIcon(name = W10MIcons.Search, color = useW10MTheme().palette.onAccent) },
                    onClick = viewModel::check,
                    modifier = Modifier.padding(horizontal = W10MSpacing.gutter).touchTarget(),
                )
                state.result?.let { result ->
                    Spacer(Modifier.height(W10MSpacing.l))
                    W10MDivider()
                    ResultSection(
                        result = result,
                        actionable = state.actionable,
                        onBlock = viewModel::block,
                        onAllow = viewModel::allow,
                        onReport = viewModel::askReport,
                    )
                }
                Spacer(Modifier.height(W10MSpacing.xl))
            }
            state.notice?.let {
                NoticeBar(notice = it, onUndo = viewModel::undo, onDismiss = viewModel::dismissNotice)
            }
            // A2: nunca banner aqui. O intersticial é disparado pelo efeito acima.
        }
        ReportCategoryDialog(
            visible = state.reporting,
            onSelect = viewModel::report,
            onDismiss = viewModel::cancelReport,
        )
    }
}

@Composable
private fun ResultSection(
    result: ScreeningResult,
    actionable: Boolean,
    onBlock: () -> Unit,
    onAllow: () -> Unit,
    onReport: () -> Unit,
) {
    val palette = useW10MTheme().palette
    val gutter = Modifier.padding(horizontal = W10MSpacing.gutter)
    val action = result.decision.action
    val summary = stringResource(R.string.ver_result_action, action.label()) + ". " +
        stringResource(R.string.ver_result_risk, result.riskLabel)
    Column(Modifier.semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        contentDescription = result.headline + ". " + summary
    }) {
        W10MText(
            text = result.headline,
            variant = W10MType::title,
            tone = when (action) {
                CallAction.BLOCK -> W10MTextTone.Danger
                CallAction.WARN -> W10MTextTone.Warn
                else -> W10MTextTone.Text
            },
            modifier = gutter.padding(top = W10MSpacing.l),
        )
        W10MText(text = stringResource(R.string.ver_result_number, result.displayNumber), modifier = gutter)
        W10MText(text = summary, tone = W10MTextTone.Muted, modifier = gutter)
        W10MText(
            text = stringResource(R.string.ver_result_confidence, result.decision.confidence.label()),
            variant = W10MType::caption,
            tone = W10MTextTone.Muted,
            modifier = gutter,
        )
        Spacer(Modifier.height(W10MSpacing.s))
        val scoreDesc = stringResource(R.string.ver_result_score_desc, result.decision.score)
        W10MScore(result.decision.score, Modifier.semantics(mergeDescendants = true) { contentDescription = scoreDesc })
    }
    Spacer(Modifier.height(W10MSpacing.m))
    W10MText(
        text = stringResource(R.string.ver_result_why),
        variant = W10MType::group,
        tone = W10MTextTone.Accent,
        modifier = gutter.semantics { heading() },
    )
    result.explanation.forEach { line ->
        W10MText(text = "• $line", modifier = gutter.padding(top = W10MSpacing.xs))
    }
    when (result.listStatus) {
        ListStatus.BLOCKED -> W10MText(
            text = stringResource(R.string.ver_result_list_blocked), tone = W10MTextTone.Muted,
            modifier = gutter.padding(top = W10MSpacing.s),
        )
        ListStatus.ALLOWED -> W10MText(
            text = stringResource(R.string.ver_result_list_allowed), tone = W10MTextTone.Muted,
            modifier = gutter.padding(top = W10MSpacing.s),
        )
        ListStatus.NONE -> Unit
    }
    W10MText(
        text = stringResource(R.string.ver_result_simulation),
        variant = W10MType::caption,
        tone = W10MTextTone.Muted,
        modifier = gutter.padding(top = W10MSpacing.s),
    )
    Spacer(Modifier.height(W10MSpacing.m))
    if (!actionable) {
        W10MText(text = stringResource(R.string.ver_hidden_note), tone = W10MTextTone.Muted, modifier = gutter)
        return
    }
    Column(gutter, verticalArrangement = Arrangement.spacedBy(W10MSpacing.xs)) {
        if (result.listStatus == ListStatus.BLOCKED) {
            W10MButton(
                label = stringResource(R.string.ver_act_unblock), variant = W10MButtonVariant.Primary, block = true,
                icon = { W10MIcon(name = W10MIcons.Check, color = palette.onAccent) },
                onClick = onAllow, modifier = Modifier.touchTarget(),
            )
        } else {
            W10MButton(
                label = stringResource(R.string.ver_act_block), variant = W10MButtonVariant.Primary, block = true,
                icon = { W10MIcon(name = W10MIcons.Block, color = palette.onAccent) },
                onClick = onBlock, modifier = Modifier.touchTarget(),
            )
            if (result.listStatus == ListStatus.NONE) {
                W10MButton(
                    label = stringResource(R.string.ver_act_allow), variant = W10MButtonVariant.Secondary, block = true,
                    icon = { W10MIcon(name = W10MIcons.Check, color = palette.text) },
                    onClick = onAllow, modifier = Modifier.touchTarget(),
                )
            }
        }
        W10MButton(
            label = stringResource(R.string.ver_act_report), variant = W10MButtonVariant.Danger, block = true,
            icon = { W10MIcon(name = W10MIcons.Flag, color = palette.onAccent) },
            onClick = onReport, modifier = Modifier.touchTarget(),
        )
    }
}
