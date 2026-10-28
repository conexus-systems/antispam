package br.com.conexus.antispam.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MEmpty
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MListItemAction
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    onNavigate: (String) -> Unit = {},
) {
    // Mock data - substituir por estado real do Room
    val history = remember { mutableStateOf<List<HistoryEntry>>(generateMockHistory()) }
    val reporting = remember { mutableStateOf<HistoryEntry?>(null) }
    val openId = remember { mutableStateOf<String?>(null) }

    W10MScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            W10MSection(
                title = null,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            ) {
                W10MText(text = stringResource(R.string.history_title), variant = W10MType::header, tone = W10MTextTone.Text)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            // Lista
            if (history.value.isEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    W10MEmpty(
                        message = stringResource(R.string.history_empty),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(history.value) { entry ->
                        val open = openId.value == entry.id
                        W10MListItem(
                            title = entry.name ?: entry.display ?: "Número oculto", // TODO: mover para strings.xml (hidden_number ausente)
                            subtitle = "${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.at))} · Score ${entry.score}",
                            leading = { W10MIcon(name = when (entry.action) {
                                "BLOCK" -> W10MIcons.Block
                                "WARN" -> W10MIcons.Warning
                                "SILENCE" -> W10MIcons.PhoneOff
                                else -> W10MIcons.Check
                            }, color = useW10MTheme().palette.actionColor(entry.action)) },
                            meta = entry.action,
                            selected = open,
                            onClick = { openId.value = if (open) null else entry.id },
                            onLongClick = { reporting.value = entry },
                            actions = if (open) listOf(
                                W10MListItemAction(
                                    label = stringResource(R.string.history_report),
                                    icon = { W10MIcon(name = W10MIcons.Flag) },
                                    onClick = { reporting.value = entry },
                                    destructive = false
                                ),
                                W10MListItemAction(
                                    label = stringResource(R.string.history_allow),
                                    icon = { W10MIcon(name = W10MIcons.Check) },
                                    onClick = { /* TODO: add to allowlist */ },
                                    destructive = false
                                ),
                                W10MListItemAction(
                                    label = stringResource(R.string.history_block),
                                    icon = { W10MIcon(name = W10MIcons.Block) },
                                    onClick = { /* TODO: add to blocklist */ },
                                    destructive = true
                                )
                            ) else emptyList(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

data class HistoryEntry(
    val id: String,
    val number: String,
    val display: String,
    val name: String?,
    val at: Long,
    val action: String,
    val score: Int,
    val reasons: List<String>,
)

private fun generateMockHistory(): List<HistoryEntry> {
    return listOf(
        HistoryEntry("1", "+551140028922", "+55 (11) 4002-8922", "Telemarketing Banco", System.currentTimeMillis() - 3600000, "BLOCK", 85, listOf("LOCAL_REPUTATION", "CAMPAIGN")),
        HistoryEntry("2", "+5521976543210", "+55 (21) 97654-3210", "Contato Desconhecido", System.currentTimeMillis() - 7200000, "ALLOW", 15, listOf()),
        HistoryEntry("3", "+5511987654321", "+55 (11) 98765-4321", "Golpe Financeiro", System.currentTimeMillis() - 10800000, "BLOCK", 92, listOf("LOCAL_REPUTATION", "STIR_SHAKEN")),
        HistoryEntry("4", "190", "190", "Polícia", System.currentTimeMillis() - 86400000, "ALLOW", 0, listOf("EMERGENCY_NUMBER")),
    )
}