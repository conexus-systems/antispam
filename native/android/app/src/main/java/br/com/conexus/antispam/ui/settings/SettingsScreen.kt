package br.com.conexus.antispam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MListItemAction
import br.com.conexus.antispam.ui.w10m.W10MPivotPager
import br.com.conexus.antispam.ui.w10m.W10MPivotPage
import br.com.conexus.antispam.ui.w10m.W10MRadio
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType
import br.com.conexus.antispam.ui.w10m.W10MToggle
import br.com.conexus.antispam.ui.w10m.W10MRow
import br.com.conexus.antispam.ui.w10m.W10MDialog
import br.com.conexus.antispam.ui.w10m.W10MDialogButton
import br.com.conexus.antispam.ui.w10m.W10MDialogButtonStyle
import br.com.conexus.antispam.ui.w10m.W10MTag
import br.com.conexus.antispam.ui.w10m.W10MTagTone
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
) {
    val confirmReset = remember { mutableStateOf(false) }
    val theme = useW10MTheme()

    // Mock state - substituir por DataStore/Room real
    val mode by remember { mutableStateOf("BALANCED") }
    val blockThreshold by remember { mutableStateOf(75) }
    val silenceThreshold by remember { mutableStateOf(85) }
    val warnThreshold by remember { mutableStateOf(55) }
    val handle0303 by remember { mutableStateOf("silence") }
    val blockUnknownInAggressive by remember { mutableStateOf(false) }
    val rules by remember { mutableStateOf<List<Rule>>(emptyList()) }
    val allowlist by remember { mutableStateOf<List<String>>(emptyList()) }
    val blacklist by remember { mutableStateOf<List<String>>(emptyList()) }
    var newRuleName by remember { mutableStateOf("") }
    var newRulePrefix by remember { mutableStateOf("") }
    var listInputAllow by remember { mutableStateOf("") }
    var listInputBlock by remember { mutableStateOf("") }
    val themeMode by remember { mutableStateOf("system") } // system, dark, light
    var accentHex by remember { mutableStateOf("#60A917") }
    val reduceMotion by remember { mutableStateOf(false) }

    val MODES = listOf(
        ModeItem("OFF", "Desativado", "Não analisa nem bloqueia nada."),
        ModeItem("BASIC", "Básico", "Bloqueia só spam de altíssima confiança."),
        ModeItem("BALANCED", "Equilibrado", "Bloqueia spam conhecido e campanhas suspeitas. Recomendado."),
        ModeItem("AGGRESSIVE", "Agressivo", "Mais restritivo. Maior risco de bloquear algo legítimo."),
        ModeItem("CUSTOM", "Personalizado", "Você define limiares e o tratamento do 0303."),
    )

    val HANDLE_0303_OPTS = listOf(
        Handle0303Item("allow", "Permitir"),
        Handle0303Item("identify", "Identificar"),
        Handle0303Item("silence", "Silenciar"),
        Handle0303Item("block", "Bloquear"),
    )

    val THEME_MODES = listOf(
        ThemeModeItem("system", "Sistema"),
        ThemeModeItem("dark", "Escuro"),
        ThemeModeItem("light", "Claro"),
    )

    @Composable
    fun ProtectionPage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Papel de filtragem
            W10MSection(title = "Identificação de chamadas") {
                W10MRow(
                    label = "Papel de filtragem",
                    value = "Verificar nas configurações",
                    onClick = { /* abrir config */ }
                )
                W10MButton(
                    label = "Conceder papel",
                    variant = W10MButtonVariant.Primary,
                    size = W10MButtonSize.Small,
                    onClick = { /* solicitar papel */ }
                )
                W10MButton(
                    label = "Abrir configurações",
                    variant = W10MButtonVariant.Secondary,
                    size = W10MButtonSize.Small,
                    onClick = { /* abrir config */ }
                )
            }

            // Modos
            W10MSection(title = "Modo de proteção") {
                MODES.forEach { m ->
                    W10MRadio(
                        selected = mode == m.id,
                        onClick = { /* set mode */ },
                        label = m.label,
                        description = m.desc,
                    )
                }
            }

            // Custom config
            if (mode == "CUSTOM") {
                W10MSection(title = "Personalizado") {
                    W10MText(text = "Bloquear a partir do score:", variant = W10MType::caption, tone = W10MTextTone.Muted)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60, 70, 75, 85, 90).forEach { v ->
                            W10MButton(
                                label = v.toString(),
                                variant = if (blockThreshold == v) W10MButtonVariant.Primary else W10MButtonVariant.Secondary,
                                size = W10MButtonSize.Small,
                                onClick = { /* set blockThreshold */ }
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                    W10MText(text = "Chamadas 0303 (telemarketing):", variant = W10MType::caption, tone = W10MTextTone.Muted)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HANDLE_0303_OPTS.forEach { h ->
                            W10MButton(
                                label = h.label,
                                variant = if (handle0303 == h.key) W10MButtonVariant.Primary else W10MButtonVariant.Secondary,
                                size = W10MButtonSize.Small,
                                onClick = { /* set handle0303 */ }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RulesPage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            W10MSection(title = "Regras (${rules.size})") {
                if (rules.isEmpty()) {
                    W10MText(text = "Nenhuma regra criada", variant = W10MType::body, tone = W10MTextTone.Muted, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(rules) { rule ->
                            W10MListItem(
                                title = rule.name,
                                subtitle = "${rule.match}: ${rule.value} · ação ${rule.action}",
                                meta = if (rule.enabled) "ativa" else "desativada",
                                trailing = {
                                    W10MTag(
                                        text = if (rule.enabled) "ativa" else "desativada",
                                        tone = if (rule.enabled) W10MTagTone.Filled else W10MTagTone.Outline,
                                        color = if (rule.enabled) useW10MTheme().palette.primary else useW10MTheme().palette.mute
                                    )
                                },
                                actions = listOf(
                                    W10MListItemAction(
                                        label = if (rule.enabled) "Desativar" else "Ativar",
                                        icon = { W10MIcon(name = if (rule.enabled) W10MIcons.Check else W10MIcons.Close) },
                                        onClick = { /* toggle */ }
                                    ),
                                    W10MListItemAction(
                                        label = "Remover",
                                        icon = { W10MIcon(name = W10MIcons.Delete) },
                                        onClick = { /* remove */ },
                                        destructive = true
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    W10MTextField(
                        label = "Nome da regra",
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        modifier = Modifier.weight(1f)
                    )
                    W10MTextField(
                        label = "Prefixo +55114002…",
                        value = newRulePrefix,
                        onValueChange = { newRulePrefix = it },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )
                }
                W10MButton(
                    label = "Adicionar regra de prefixo (bloquear)",
                    variant = W10MButtonVariant.Primary,
                    icon = { W10MIcon(name = W10MIcons.Add) },
                    onClick = { /* add rule */ }
                )
            }
        }
    }

    @Composable
    fun ListsPage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            W10MSection(title = "Permitidos (${allowlist.size})") {
                if (allowlist.isEmpty()) {
                    W10MText(text = "Nenhum número na lista de permitidos", variant = W10MType::caption, tone = W10MTextTone.Muted, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(allowlist) { v ->
                            W10MListItem(
                                title = v,
                                trailing = {
                                    W10MIcon(name = W10MIcons.Delete, color = useW10MTheme().palette.danger)
                                },
                                onClick = { /* remove allow */ }
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    W10MTextField(
                        label = "Número +5511999999999",
                        value = listInputAllow,
                        onValueChange = { listInputAllow = it },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )
                    W10MButton(
                        label = "Adicionar",
                        variant = W10MButtonVariant.Primary,
                        size = W10MButtonSize.Small,
                        onClick = { /* add allow */ }
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 24.dp))

            W10MSection(title = "Bloqueados (${blacklist.size})") {
                if (blacklist.isEmpty()) {
                    W10MText(text = "Nenhum número na lista de bloqueados", variant = W10MType::caption, tone = W10MTextTone.Muted, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(blacklist) { v ->
                            W10MListItem(
                                title = v,
                                trailing = {
                                    W10MIcon(name = W10MIcons.Delete, color = useW10MTheme().palette.danger)
                                },
                                onClick = { /* remove block */ }
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    W10MTextField(
                        label = "Número +5511999999999",
                        value = listInputBlock,
                        onValueChange = { listInputBlock = it },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )
                    W10MButton(
                        label = "Adicionar",
                        variant = W10MButtonVariant.Primary,
                        size = W10MButtonSize.Small,
                        onClick = { /* add block */ }
                    )
                }
            }
        }
    }

    @Composable
    fun GeneralPage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            W10MSection(title = "Conta Conexus (opcional)") {
                W10MRow(
                    label = "Conta",
                    value = "Em breve · o app funciona sem conta",
                    onClick = { onNavigate("account") }
                )
            }

            W10MSection(title = "IA Assistente (OpenRouter)") {
                W10MToggle(
                    label = "IA Assistente",
                    description = "Off por padrão · nunca decide bloqueios",
                    checked = false,
                    onCheckedChange = { /* toggle */ }
                )
            }

            W10MSection(title = "Backup") {
                W10MText(text = "Exporta modo, regras e listas em JSON validado. Import rejeita payloads maliciosos.", variant = W10MType::body, tone = W10MTextTone.Muted)
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    W10MButton(
                        label = "Exportar (copiar)",
                        variant = W10MButtonVariant.Secondary,
                        size = W10MButtonSize.Small,
                        block = true,
                        onClick = { /* export */ }
                    )
                    W10MButton(
                        label = "Importar (colar)",
                        variant = W10MButtonVariant.Secondary,
                        size = W10MButtonSize.Small,
                        block = true,
                        onClick = { /* import */ }
                    )
                }
            }

            W10MSection(title = "Base local") {
                W10MRow(label = "Registros de reputação", value = "0")
                W10MRow(label = "Chamadas no histórico", value = "0")
                W10MRow(label = "Armazenamento", value = "somente no aparelho", valueTone = W10MTextTone.Accent)
            }

            W10MSection(title = "Sobre") {
                W10MRow(label = "Versão", value = "1.0.0")
                W10MRow(label = "Licença", value = "MIT (open source)")
                W10MRow(label = "Telemetria", value = "nenhuma", valueTone = W10MTextTone.Accent)
                W10MRow(label = "Anúncios", value = "banner e intersticial ocasional (nunca durante chamadas)")
            }

            W10MSection(title = "Reset") {
                W10MButton(
                    label = "Limpar histórico",
                    variant = W10MButtonVariant.Danger,
                    size = W10MButtonSize.Small,
                    onClick = { confirmReset.value = true }
                )
            }

            W10MSection() {
                W10MText(text = "Conexus Caller ID · fail-safe: em caso de dúvida, a chamada é permitida.", variant = W10MType::caption, tone = W10MTextTone.Muted)
            }
        }
    }

    @Composable
    fun AppearancePage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            W10MSection(title = "Tema") {
                THEME_MODES.forEach { t ->
                    W10MRadio(
                        selected = themeMode == t.key,
                        onClick = { /* set theme */ },
                        label = t.label,
                        description = null
                    )
                }
            }

            W10MSection(title = "Cor de destaque") {
                val presets = listOf(
                    "#60A917", "#2E7D32", "#AA00FF", "#FA6800",
                    "#E51400", "#E671B8", "#1BA1E2", "#F0A30A"
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { hex ->
                        val selected = accentHex == hex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(if (selected) 3.dp else 2.dp, if (selected) useW10MTheme().palette.primary else useW10MTheme().palette.border, RectangleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)), RectangleShape)
                                .clickable { accentHex = hex }
                        ) {}
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                W10MTextField(
                    label = "Cor personalizada",
                    value = "",
                    onValueChange = { /* validate hex */ },
                    placeholder = "#RRGGBB"
                )
                W10MButton(
                    label = "Restaurar cor padrão",
                    variant = W10MButtonVariant.Secondary,
                    size = W10MButtonSize.Small,
                    onClick = { accentHex = "#60A917" }
                )
            }

            W10MSection(title = "Movimento") {
                W10MToggle(
                    label = "Reduzir animações",
                    description = "Desliga o movimento das telas e dos controles.",
                    checked = reduceMotion,
                    onCheckedChange = { /* set */ }
                )
            }
        }
    }

    W10MScreen {
        W10MPivotPager(
            pages = listOf(
                W10MPivotPage("protection", "Proteção") { ProtectionPage() },
                W10MPivotPage("rules", "Regras") { RulesPage() },
                W10MPivotPage("lists", "Listas") { ListsPage() },
                W10MPivotPage("general", "Geral") { GeneralPage() },
                W10MPivotPage("appearance", "Aparência") { AppearancePage() },
            ),
            selectedKey = "protection", // TODO: estado real
            onChange = { /* navegar */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}

data class ModeItem(val id: String, val label: String, val desc: String)
data class Handle0303Item(val key: String, val label: String)
data class ThemeModeItem(val key: String, val label: String)
data class Rule(val id: String, val name: String, val enabled: Boolean, val action: String, val match: String, val value: String)
