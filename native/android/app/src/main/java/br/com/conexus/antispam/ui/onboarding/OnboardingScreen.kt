package br.com.conexus.antispam.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.home.ProtectionRole
import br.com.conexus.antispam.ui.home.rememberNotificationRequester
import br.com.conexus.antispam.ui.home.rememberRoleRequester
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.W10MType
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MNotice
import br.com.conexus.antispam.ui.w10m.W10MNoticeTone
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone

private val MinTouch = 48.dp

/** Sem anúncios aqui (regra A2). A conclusão é gravada em UserSettings.onboardingDone pelo ViewModel. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isLast = step == viewModel.stepCount - 1

    BackHandler(enabled = step > 0) { viewModel.back() }

    val requestRole = rememberRoleRequester { held ->
        viewModel.onRoleResult(held, ProtectionRole.isAvailable(context))
    }
    // Pedir ou recusar, o onboarding termina: o app segue utilizável e a Home avisa o que faltou.
    val requestNotifications = rememberNotificationRequester { viewModel.finish() }

    W10MScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = W10MSpacing.xl, horizontal = W10MSpacing.gutter),
        ) {
            W10MText(
                text = stringResource(R.string.ob_step_progress, step + 1, viewModel.stepCount),
                variant = W10MType::kicker,
                tone = W10MTextTone.Accent,
            )
            Spacer(Modifier.height(W10MSpacing.l))

            when (step) {
                0 -> DisclosureStep(onAgree = viewModel::next)
                1 -> RoleStep(
                    role = role,
                    isLast = isLast,
                    onActivate = requestRole,
                    onContinue = { if (isLast) viewModel.finish() else viewModel.next() },
                )
                else -> NotificationsStep(onAllow = requestNotifications, onSkip = viewModel::finish)
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    val palette = useW10MTheme().palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        W10MIcon(
            name = W10MIcons.Shield,
            size = 40.dp,
            color = palette.primary,
            modifier = Modifier.semantics { contentDescription = "" },
        )
        Spacer(Modifier.width(W10MSpacing.m))
        W10MText(text = title, variant = W10MType::header, tone = W10MTextTone.Text, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(W10MSpacing.m))
    W10MText(text = body, variant = W10MType::body, tone = W10MTextTone.Muted)
}

@Composable
private fun DisclosureStep(onAgree: () -> Unit) {
    val palette = useW10MTheme().palette
    StepHeader(stringResource(R.string.ob_welcome_title), stringResource(R.string.ob_welcome_body))
    Spacer(Modifier.height(W10MSpacing.l))

    // Divulgação em destaque: texto integral da spec §8.2, antes de qualquer pedido do sistema.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(W10MSpacing.l),
    ) {
        W10MText(
            text = stringResource(R.string.ob_disclosure_title),
            variant = W10MType::title,
            tone = W10MTextTone.Text,
        )
        Spacer(Modifier.height(W10MSpacing.s))
        W10MText(
            text = stringResource(R.string.ob_disclosure_text),
            variant = W10MType::body,
            tone = W10MTextTone.Text,
        )
    }
    Spacer(Modifier.height(W10MSpacing.m))
    Point(stringResource(R.string.ob_point_local))
    Point(stringResource(R.string.ob_point_contacts))
    Point(stringResource(R.string.ob_point_nosend))
    Point(stringResource(R.string.ob_point_calls))

    Spacer(Modifier.height(W10MSpacing.l))
    W10MButton(
        label = stringResource(R.string.ob_agree),
        onClick = onAgree,
        variant = W10MButtonVariant.Primary,
        block = true,
        modifier = Modifier.heightIn(min = MinTouch),
    )
}

@Composable
private fun Point(text: String) {
    val palette = useW10MTheme().palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        W10MIcon(
            name = W10MIcons.Check,
            size = 20.dp,
            color = palette.primary,
            modifier = Modifier.semantics { contentDescription = "" },
        )
        Spacer(Modifier.width(W10MSpacing.m))
        W10MText(text = text, variant = W10MType::body, tone = W10MTextTone.Text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RoleStep(role: RoleOutcome, isLast: Boolean, onActivate: () -> Unit, onContinue: () -> Unit) {
    StepHeader(stringResource(R.string.ob_role_title), stringResource(R.string.ob_role_body))
    Spacer(Modifier.height(W10MSpacing.l))

    when (role) {
        RoleOutcome.NOT_ASKED -> {
            W10MButton(
                label = stringResource(R.string.ob_role_activate),
                onClick = onActivate,
                variant = W10MButtonVariant.Primary,
                block = true,
                modifier = Modifier.heightIn(min = MinTouch),
            )
            Spacer(Modifier.height(W10MSpacing.s))
            W10MButton(
                label = stringResource(R.string.ob_role_later),
                onClick = onContinue,
                variant = W10MButtonVariant.Text,
                block = true,
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
        else -> {
            val (message, tone) = when (role) {
                RoleOutcome.GRANTED -> R.string.ob_role_granted to W10MNoticeTone.Success
                RoleOutcome.UNAVAILABLE -> R.string.ob_role_unavailable to W10MNoticeTone.Warning
                else -> R.string.ob_role_denied to W10MNoticeTone.Warning
            }
            W10MNotice(message = stringResource(message), tone = tone, modifier = Modifier.padding(horizontal = 0.dp))
            Spacer(Modifier.height(W10MSpacing.s))
            W10MButton(
                label = stringResource(if (isLast) R.string.ob_finish else R.string.onboarding_continue),
                onClick = onContinue,
                variant = W10MButtonVariant.Primary,
                block = true,
                modifier = Modifier.heightIn(min = MinTouch),
            )
            if (role == RoleOutcome.DENIED) {
                Spacer(Modifier.height(W10MSpacing.s))
                W10MButton(
                    label = stringResource(R.string.ob_role_activate),
                    onClick = onActivate,
                    variant = W10MButtonVariant.Text,
                    block = true,
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            }
        }
    }
}

@Composable
private fun NotificationsStep(onAllow: () -> Unit, onSkip: () -> Unit) {
    StepHeader(stringResource(R.string.ob_notif_title), stringResource(R.string.ob_notif_body))
    Spacer(Modifier.height(W10MSpacing.l))
    W10MButton(
        label = stringResource(R.string.ob_notif_allow),
        onClick = onAllow,
        variant = W10MButtonVariant.Primary,
        block = true,
        modifier = Modifier.heightIn(min = MinTouch),
    )
    Spacer(Modifier.height(W10MSpacing.s))
    W10MButton(
        label = stringResource(R.string.ob_notif_skip),
        onClick = onSkip,
        variant = W10MButtonVariant.Text,
        block = true,
        modifier = Modifier.heightIn(min = MinTouch),
    )
}
