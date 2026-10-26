package br.com.conexus.antispam.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.spacer
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val simulatedNumber by remember { mutableStateOf("") }
    val lastDecision by remember { mutableStateOf<DecisionResult?>(null) }

    W10MScreen {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header com kicker
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                W10MText(
                    text = stringResource(R.string.app_name),
                    variant = W10MType::kicker,
                    tone = W10MTextTone.Accent
                )
                W10MText(
                    text = stringResource(R.string.home_subtitle),
                    variant = W10MType::body,
                    tone = W10MTextTone.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

            // Card de simulação (flat, sem elevação, sem cantos arredondados)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = 0.dp,
                shape = androidx.compose.foundation.shape.RectangleShape,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = useW10MTheme().palette.chrome
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    W10MText(
                        text = stringResource(R.string.home_simulator_title),
                        variant = W10MType::title,
                        tone = W10MTextTone.Text,
                        textAlign = TextAlign.Center
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

                    // Input do número (W10MTextField)
                    androidx.compose.material3.TextField(
                        value = simulatedNumber,
                        onValueChange = { simulatedNumber = it },
                        label = { W10MText(text = stringResource(R.string.home_simulator_hint), variant = W10MType::body, tone = W10MTextTone.Text) },
                        placeholder = { W10MText(text = stringResource(R.string.home_simulator_placeholder), variant = W10MType::body, tone = W10MTextTone.Muted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        ),
                        colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                            backgroundColor = useW10MTheme().palette.surface,
                            focusedIndicatorColor = useW10MTheme().palette.accent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = useW10MTheme().palette.accent,
                            textColor = useW10MTheme().palette.text,
                            placeholderColor = useW10MTheme().palette.textDim,
                        )
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

                    W10MButton(
                        label = stringResource(R.string.home_simulate_button),
                        variant = W10MButtonVariant.Primary,
                        block = true,
                        onClick = {
                            if (simulatedNumber.isNotBlank()) {
                                scope.launch {
                                    val decision = simulateCall(simulatedNumber)
                                    lastDecision = decision
                                }
                            }
                        }
                    )
                }
            }

            // Resultado da simulação
            lastDecision?.let { decision ->
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = 0.dp,
                    shape = androidx.compose.foundation.shape.RectangleShape,
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = when (decision.action) {
                            "BLOCK" -> useW10MTheme().palette.dangerFill.copy(alpha = 0.1f)
                            "SILENCE" -> useW10MTheme().palette.warnFill.copy(alpha = 0.1f)
                            "WARN" -> useW10MTheme().palette.info.copy(alpha = 0.1f)
                            else -> useW10MTheme().palette.primary.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        W10MIcon(
                            name = when (decision.action) {
                                "BLOCK" -> W10MIcons.Block
                                "SILENCE" -> W10MIcons.Warning
                                "WARN" -> W10MIcons.Warning
                                else -> W10MIcons.Check
                            },
                            size = 48.dp,
                            color = when (decision.action) {
                                "BLOCK" -> useW10MTheme().palette.danger
                                "SILENCE" -> useW10MTheme().palette.warn
                                "WARN" -> useW10MTheme().palette.info
                                else -> useW10MTheme().palette.primary
                            }
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        W10MText(
                            text = decision.action,
                            variant = W10MType::header,
                            tone = W10MTextTone.Text,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        W10MText(
                            text = "${decision.display} · Score ${decision.score}/100",
                            variant = W10MType::body,
                            tone = W10MTextTone.Muted
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                        W10MText(
                            text = decision.explanation.joinToString("\n"),
                            variant = W10MType::caption,
                            tone = W10MTextTone.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))

            // Links rápidos (W10MListItem style)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionItem(
                    icon = { W10MIcon(name = W10MIcons.History) },
                    title = stringResource(R.string.home_history),
                    subtitle = stringResource(R.string.home_history_desc),
                    onClick = { onNavigate("history") }
                )
                QuickActionItem(
                    icon = { W10MIcon(name = W10MIcons.Search) },
                    title = stringResource(R.string.home_lookup),
                    subtitle = stringResource(R.string.home_lookup_desc),
                    onClick = { onNavigate("verify") }
                )
                QuickActionItem(
                    icon = { W10MIcon(name = W10MIcons.Settings) },
                    title = stringResource(R.string.home_settings),
                    subtitle = stringResource(R.string.home_settings_desc),
                    onClick = { onNavigate("settings") }
                )
            }
        }
    }
}

@Composable
fun QuickActionItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val palette = useW10MTheme().palette
    
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .minHeight(48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = null)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
            Column {
                W10MText(text = title, variant = W10MType::body, tone = W10MTextTone.Text)
                W10MText(text = subtitle, variant = W10MType::caption, tone = W10MTextTone.Muted)
            }
        }
    }
}

data class DecisionResult(
    val action: String,
    val score: Int,
    val display: String,
    val explanation: List<String>
)

private suspend fun simulateCall(number: String): DecisionResult {
    val normalized = number.replace("[^0-9+]".toRegex(), "")
    val isSpam = normalized.contains("4002") || normalized.contains("4003") || normalized.contains("9999")
    val action = if (isSpam) "BLOCK" else "ALLOW"
    val score = if (isSpam) 85 else 10

    return DecisionResult(
        action = action,
        score = score,
        display = formatDisplay(number),
        explanation = if (isSpam)
            listOf("Número na base de telemarketing conhecida", "Padrão 4002/4003 detectado")
        else
            listOf("Nenhum sinal de risco encontrado")
    )
}

private fun formatDisplay(raw: String): String {
    val digits = raw.replace("[^0-9]".toRegex(), "")
    return when {
        digits.startsWith("55") && digits.length >= 12 -> {
            val ddd = digits.substring(2, 4)
            val num = digits.substring(4)
            if (num.length == 9) "+55 ($ddd) ${num.substring(0,5)}-${num.substring(5)}"
            else if (num.length == 8) "+55 ($ddd) ${num.substring(0,4)}-${num.substring(4)}"
            else raw
        }
        digits.length == 11 -> {
            val ddd = digits.substring(0, 2)
            val num = digits.substring(2)
            "($ddd) ${num.substring(0,5)}-${num.substring(5)}"
        }
        digits.length == 10 -> {
            val ddd = digits.substring(0, 2)
            val num = digits.substring(2)
            "($ddd) ${num.substring(0,4)}-${num.substring(4)}"
        }
        else -> raw
    }
}