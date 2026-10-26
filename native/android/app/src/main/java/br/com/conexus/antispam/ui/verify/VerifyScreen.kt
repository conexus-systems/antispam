package br.com.conexus.antispam.ui.verify

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
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.launch

@Composable
fun VerifyScreen(
    onNavigate: (String) -> Unit = {},
) {
    val phoneNumber by remember { mutableStateOf("") }
    val result by remember { mutableStateOf<VerifyResult?>(null) }
    val scope = rememberCoroutineScope()

    W10MScreen {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            W10MSection(modifier = Modifier.padding(horizontal = 16.dp, top = 16.dp)) {
                W10MText(text = "Verificar número", variant = W10MType::header, tone = W10MTextTone.Text)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

            // Input field
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
                Column(modifier = Modifier.padding(16.dp)) {
                    W10MTextField(
                        label = "Número para verificar",
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = "Ex: +55 11 99999-9999",
                        helperText = "Consulta k-anônima: apenas prefixo enviado à API",
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

                    W10MButton(
                        label = "Verificar",
                        variant = W10MButtonVariant.Primary,
                        block = true,
                        icon = { W10MIcon(name = W10MIcons.Search) },
                        onClick = {
                            if (phoneNumber.isNotBlank()) {
                                scope.launch {
                                    result.value = verifyNumber(phoneNumber)
                                }
                            }
                        }
                    )
                }
            }

            // Result
            result?.let { res ->
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = 0.dp,
                    shape = androidx.compose.foundation.shape.RectangleShape,
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = when (res.action) {
                            "BLOCK" -> useW10MTheme().palette.dangerFill.copy(alpha = 0.1f)
                            "WARN" -> useW10MTheme().palette.warnFill.copy(alpha = 0.1f)
                            "SILENCE" -> useW10MTheme().palette.info.copy(alpha = 0.1f)
                            else -> useW10MTheme().palette.primary.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        W10MText(text = "Resultado da verificação", variant = W10MType::title, tone = W10MTextTone.Text)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))

                        W10MSection(title = "Score") {
                            W10MScore(score = res.score)
                        }

                        W10MSection(title = "Por que este score?") {
                            res.explanation.forEach { reason ->
                                W10MRow(label = "•", value = reason)
                            }
                        }

                        W10MSection(title = "Ações rápidas") {
                            W10MButton(
                                label = "Denunciar como spam",
                                variant = W10MButtonVariant.Danger,
                                block = true,
                                onClick = { /* report */ }
                            )
                            W10MButton(
                                label = "Sempre permitir",
                                variant = W10MButtonVariant.Secondary,
                                block = true,
                                onClick = { /* allowlist */ }
                            )
                            W10MButton(
                                label = "Bloquear número",
                                variant = W10MButtonVariant.Primary,
                                block = true,
                                onClick = { /* blocklist */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class VerifyResult(
    val action: String,
    val score: Int,
    val explanation: List<String>,
)

private suspend fun verifyNumber(number: String): VerifyResult {
    // TODO: Implementar chamada real à API k-anônima
    val normalized = number.replace("[^0-9+]".toRegex(), "")
    val isSpam = normalized.contains("4002") || normalized.contains("4003") || normalized.contains("9999")
    val action = if (isSpam) "BLOCK" else "ALLOW"
    val score = if (isSpam) 85 else 10

    return VerifyResult(
        action = action,
        score = score,
        explanation = if (isSpam)
            listOf("Número na base de telemarketing conhecida", "Padrão 4002/4003 detectado", "Múltiplas denúncias na comunidade")
        else
            listOf("Nenhum sinal de risco encontrado na base local", "Consulta k-anônima não retornou reputação negativa")
    )
}