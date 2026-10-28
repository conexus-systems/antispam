package br.com.conexus.antispam.ui.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val activating by viewModel.activating.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity

    val steps = remember {
        listOf(
            OnboardingStep(
                icon = { W10MIcon(name = W10MIcons.PhoneOff, size = 72.dp) },
                titleRes = R.string.onboarding_step1_title,
                bodyRes = R.string.onboarding_step1_body
            ),
            OnboardingStep(
                icon = { W10MIcon(name = W10MIcons.Lock, size = 72.dp) },
                titleRes = R.string.onboarding_step2_title,
                bodyRes = R.string.onboarding_step2_body
            ),
            OnboardingStep(
                icon = { W10MIcon(name = W10MIcons.Shield, size = 72.dp) },
                titleRes = R.string.onboarding_step3_title,
                bodyRes = R.string.onboarding_step3_body
            )
        )
    }

    val step = steps[currentStep]
    val isLastStep = currentStep == steps.lastIndex

    W10MScreen {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kicker
            W10MText(
                text = stringResource(R.string.app_name),
                variant = W10MType::kicker,
                tone = W10MTextTone.Accent,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Indicador de passos
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                steps.indices.forEach { i ->
                    val selected = i == currentStep
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(if (selected) 24.dp else 8.dp, 8.dp)
                            .padding(4.dp)
                            .background(if (selected) useW10MTheme().palette.primary else useW10MTheme().palette.border)
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

            // Ícone
            step.icon()

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

            // Título
            W10MText(
                text = stringResource(step.titleRes),
                variant = W10MType::header,
                tone = W10MTextTone.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            // Corpo
            W10MText(
                text = stringResource(step.bodyRes),
                variant = W10MType::body,
                tone = W10MTextTone.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(32.dp))

            // Botões
            Column(
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isLastStep) {
                    W10MButton(
                        label = stringResource(R.string.onboarding_continue),
                        variant = W10MButtonVariant.Primary,
                        block = true,
                        onClick = { viewModel.nextStep() }
                    )

                    W10MButton(
                        label = stringResource(R.string.onboarding_skip),
                        variant = W10MButtonVariant.Text,
                        block = true,
                        onClick = onFinish
                    )
                } else {
                    W10MButton(
                        label = if (activating) stringResource(R.string.onboarding_activating) else stringResource(R.string.onboarding_activate),
                        variant = W10MButtonVariant.Primary,
                        block = true,
                        loading = activating,
                        icon = { W10MIcon(name = W10MIcons.Shield, size = 18.dp, color = useW10MTheme().palette.onAccent) },
                        onClick = { viewModel.activateProtection(activity) }
                    )

                    W10MButton(
                        label = stringResource(R.string.onboarding_demo_mode),
                        variant = W10MButtonVariant.Text,
                        block = true,
                        onClick = onFinish
                    )
                }
            }
        }
    }
}

data class OnboardingStep(
    val icon: @Composable () -> Unit,
    val titleRes: Int,
    val bodyRes: Int
)