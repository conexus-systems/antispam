package br.com.conexus.antispam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ads.AdBanner
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.DatasetInfo
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonSize
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MIconButton
import br.com.conexus.antispam.ui.w10m.W10MListItem
import br.com.conexus.antispam.ui.w10m.W10MRadio
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MToggle
import br.com.conexus.antispam.ui.w10m.W10MType

private val ACCENT_PRESETS = listOf(
    "#1BA1E2", "#60A917", "#2E7D32", "#AA00FF",
    "#FA6800", "#E51400", "#E671B8", "#F0A30A",
)

@Composable
fun SettingsScreen(
    onOpenLists: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val vm: SettingsViewModel = hiltViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dataset by vm.dataset.collectAsStateWithLifecycle()
    val ads by vm.adsState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    W10MScreen {
        Column(Modifier.fillMaxSize()) {
            val s = settings
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                W10MText(
                    text = stringResource(R.string.settings_screen_title),
                    variant = W10MType::header,
                    tone = W10MTextTone.Text,
                    modifier = Modifier.padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.m),
                )
                if (s != null) {
                    ProtectionSection(s, vm)
                    SectionGap()
                    SpecialNumbersSection(s, vm)
                    SectionGap()
                    NotificationsSection(s, vm)
                    SectionGap()
                    AppearanceSection(s, vm)
                    SectionGap()
                    ManageSection(onOpenLists, onOpenRules, onOpenPrivacy)
                    SectionGap()
                    DatasetSection(dataset)
                    if (ads.privacyOptionsRequired && activity != null) {
                        SectionGap()
                        W10MListItem(
                            title = stringResource(R.string.settings_ads_privacy_options),
                            subtitle = stringResource(R.string.settings_ads_privacy_options_desc),
                            onClick = { vm.adsManager.showPrivacyOptions(activity) },
                            modifier = Modifier.heightIn(min = MinTouch),
                        )
                    }
                }
                SectionGap()
            }
            // Banner discreto no rodapé; ocupa 0 de altura sem consentimento ou se falhar (spec A2/A10).
            AdBanner(vm.adsManager)
        }
    }
}

// ---------------------------------------------------------------- Proteção

@Composable
private fun ProtectionSection(s: UserSettings, vm: SettingsViewModel) {
    val current = runCatching { ProtectionMode.valueOf(s.mode) }.getOrDefault(ProtectionMode.BALANCED)
    W10MSection(title = stringResource(R.string.settings_section_protection)) {
        ProtectionMode.entries.forEach { m ->
            W10MRadio(
                selected = current == m,
                onClick = { vm.setMode(m) },
                label = stringResource(m.labelRes()),
                description = stringResource(m.descRes()),
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
    }
    if (current == ProtectionMode.CUSTOM) {
        SectionGap()
        W10MSection(title = stringResource(R.string.settings_section_thresholds)) {
            HintText(stringResource(R.string.settings_thresholds_hint))
            val warnLabel = stringResource(R.string.settings_threshold_warn)
            val blockLabel = stringResource(R.string.settings_threshold_block)
            val silenceLabel = stringResource(R.string.settings_threshold_silence)
            ThresholdStepper(warnLabel, s.warnThreshold, 0..100) { vm.setThresholds(it, s.blockThreshold, s.silenceThreshold) }
            ThresholdStepper(blockLabel, s.blockThreshold, 0..100) { vm.setThresholds(s.warnThreshold, it, s.silenceThreshold) }
            ThresholdStepper(silenceLabel, s.silenceThreshold, 0..101) { vm.setThresholds(s.warnThreshold, s.blockThreshold, it) }
        }
    }
    if (current == ProtectionMode.AGGRESSIVE) {
        W10MToggle(
            checked = s.blockUnknownInAggressive,
            onCheckedChange = vm::setBlockUnknownInAggressive,
            label = stringResource(R.string.settings_block_unknown_title),
            description = stringResource(R.string.settings_block_unknown_desc),
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
}

private fun ProtectionMode.labelRes() = when (this) {
    ProtectionMode.OFF -> R.string.settings_mode_off
    ProtectionMode.BASIC -> R.string.settings_mode_basic
    ProtectionMode.BALANCED -> R.string.settings_mode_balanced
    ProtectionMode.AGGRESSIVE -> R.string.settings_mode_aggressive
    ProtectionMode.CUSTOM -> R.string.settings_mode_custom
}

private fun ProtectionMode.descRes() = when (this) {
    ProtectionMode.OFF -> R.string.settings_mode_off_desc
    ProtectionMode.BASIC -> R.string.settings_mode_basic_desc
    ProtectionMode.BALANCED -> R.string.settings_mode_balanced_desc
    ProtectionMode.AGGRESSIVE -> R.string.settings_mode_aggressive_desc
    ProtectionMode.CUSTOM -> R.string.settings_mode_custom_desc
}

/** Passo de 5 em 5; o repositório garante warn <= block <= silence. 101 = "Desligado" (silenciar). */
@Composable
private fun ThresholdStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val palette = useW10MTheme().palette
    val off = stringResource(R.string.settings_threshold_silence_off)
    val shown = if (value > 100) off else value.toString()
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        W10MText(text = label, variant = W10MType::body, modifier = Modifier.weight(1f))
        W10MIconButton(
            icon = { Icon(Icons.Default.Remove, null, tint = palette.text) },
            onClick = { onChange((value - 5).coerceAtLeast(range.first)) },
            contentDescription = stringResource(R.string.settings_threshold_decrease, label),
            modifier = Modifier.size(MinTouch),
        )
        W10MText(
            text = shown,
            variant = W10MType::group,
            tone = W10MTextTone.Accent,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        W10MIconButton(
            icon = { Icon(Icons.Default.Add, null, tint = palette.text) },
            onClick = { onChange((value + 5).coerceAtMost(range.last)) },
            contentDescription = stringResource(R.string.settings_threshold_increase, label),
            modifier = Modifier.size(MinTouch),
        )
    }
}

// ---------------------------------------------------------------- Números especiais

@Composable
private fun SpecialNumbersSection(s: UserSettings, vm: SettingsViewModel) {
    W10MSection(title = stringResource(R.string.settings_section_special)) {
        W10MText(
            text = stringResource(R.string.settings_special_0303_title),
            variant = W10MType::body,
            modifier = Modifier.padding(bottom = W10MSpacing.xs),
        )
        listOf(
            "allow" to R.string.settings_0303_allow,
            "identify" to R.string.settings_0303_identify,
            "silence" to R.string.settings_0303_silence,
            "block" to R.string.settings_0303_block,
        ).forEach { (key, res) ->
            W10MRadio(
                selected = s.handle0303 == key,
                onClick = { vm.setHandle0303(key) },
                label = stringResource(res),
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
    }
    W10MToggle(
        checked = s.blockHidden,
        onCheckedChange = vm::setBlockHidden,
        label = stringResource(R.string.settings_block_hidden_title),
        description = stringResource(R.string.settings_block_hidden_desc),
        modifier = Modifier.heightIn(min = MinTouch),
    )
    W10MToggle(
        checked = s.blockInternational,
        onCheckedChange = vm::setBlockInternational,
        label = stringResource(R.string.settings_block_international_title),
        description = stringResource(R.string.settings_block_international_desc),
        modifier = Modifier.heightIn(min = MinTouch),
    )
    W10MToggle(
        checked = s.rejectBlockedCalls,
        onCheckedChange = vm::setRejectBlockedCalls,
        label = stringResource(R.string.settings_reject_title),
        description = stringResource(R.string.settings_reject_desc),
        modifier = Modifier.heightIn(min = MinTouch),
    )
}

// ---------------------------------------------------------------- Notificações

@Composable
private fun NotificationsSection(s: UserSettings, vm: SettingsViewModel) {
    W10MSection(title = stringResource(R.string.settings_section_notifications)) {
        W10MToggle(
            checked = s.notificationsEnabled,
            onCheckedChange = vm::setNotificationsEnabled,
            label = stringResource(R.string.settings_notifications_title),
            description = stringResource(R.string.settings_notifications_desc),
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
}

// ---------------------------------------------------------------- Aparência

@Composable
private fun AppearanceSection(s: UserSettings, vm: SettingsViewModel) {
    val palette = useW10MTheme().palette
    W10MSection(title = stringResource(R.string.settings_section_appearance)) {
        listOf(
            "system" to R.string.settings_theme_system,
            "dark" to R.string.settings_theme_dark,
            "light" to R.string.settings_theme_light,
        ).forEach { (key, res) ->
            W10MRadio(
                selected = s.theme == key,
                onClick = { vm.setTheme(key) },
                label = stringResource(res),
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }

        W10MText(
            text = stringResource(R.string.settings_accent_heading),
            variant = W10MType::body,
            modifier = Modifier.padding(top = W10MSpacing.m, bottom = W10MSpacing.s),
        )
        val currentAccent = s.accent.uppercase()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(W10MSpacing.s)) {
            items(ACCENT_PRESETS) { hex ->
                val selected = currentAccent == hex
                val optionDesc = stringResource(R.string.settings_accent_option, hex)
                Box(
                    modifier = Modifier
                        .size(MinTouch)
                        .border(if (selected) 3.dp else 1.dp, if (selected) palette.text else palette.border, RectangleShape)
                        .padding(4.dp)
                        .background(Color(android.graphics.Color.parseColor(hex)), RectangleShape)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                            contentDescription = optionDesc
                        }
                        .clickable { vm.setAccent(hex) },
                )
            }
        }

        var custom by remember { mutableStateOf("") }
        var customError by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(top = W10MSpacing.m),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(W10MSpacing.s),
        ) {
            W10MTextField(
                label = stringResource(R.string.settings_accent_custom),
                value = custom,
                onValueChange = { custom = it.take(7); customError = false },
                placeholder = stringResource(R.string.settings_accent_custom_placeholder),
                errorText = if (customError) stringResource(R.string.settings_accent_custom_error) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
            W10MButton(
                label = stringResource(R.string.settings_accent_apply),
                variant = W10MButtonVariant.Primary,
                size = W10MButtonSize.Small,
                onClick = {
                    if (vm.setAccent(custom)) { custom = ""; customError = false } else customError = true
                },
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
        W10MButton(
            label = stringResource(R.string.settings_accent_reset),
            variant = W10MButtonVariant.Secondary,
            size = W10MButtonSize.Small,
            onClick = vm::resetAccent,
            modifier = Modifier.heightIn(min = MinTouch),
        )
    }
    W10MToggle(
        checked = s.reduceMotion,
        onCheckedChange = vm::setReduceMotion,
        label = stringResource(R.string.settings_reduce_motion_title),
        description = stringResource(R.string.settings_motion_desc),
        modifier = Modifier.heightIn(min = MinTouch),
    )
}

// ---------------------------------------------------------------- Atalhos e base local

@Composable
private fun ManageSection(onOpenLists: () -> Unit, onOpenRules: () -> Unit, onOpenPrivacy: () -> Unit) {
    W10MSection(title = stringResource(R.string.settings_section_manage)) {}
    NavItem(R.string.settings_open_lists, R.string.settings_open_lists_desc, onOpenLists)
    NavItem(R.string.settings_open_rules, R.string.settings_open_rules_desc, onOpenRules)
    NavItem(R.string.settings_open_privacy, R.string.settings_open_privacy_desc, onOpenPrivacy)
}

@Composable
private fun NavItem(title: Int, subtitle: Int, onClick: () -> Unit) {
    W10MListItem(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onClick = onClick,
        trailing = { W10MText(text = "›", variant = W10MType::subtitle, tone = W10MTextTone.Muted) },
        modifier = Modifier.heightIn(min = MinTouch),
    )
}

@Composable
private fun DatasetSection(dataset: DatasetInfo?) {
    W10MSection(title = stringResource(R.string.settings_section_dataset)) {
        if (dataset == null) {
            W10MText(text = stringResource(R.string.settings_dataset_none), variant = W10MType::body, tone = W10MTextTone.Muted)
        } else {
            W10MText(
                text = stringResource(R.string.settings_dataset_version, dataset.version, dataset.publishedAt),
                variant = W10MType::body,
            )
            W10MText(
                text = stringResource(R.string.settings_dataset_entries, dataset.entries),
                variant = W10MType::caption,
                tone = W10MTextTone.Muted,
            )
        }
    }
}
