package br.com.conexus.antispam.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.conexus.antispam.R
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.domain.RuleItem
import br.com.conexus.antispam.domain.RuleKind
import br.com.conexus.antispam.ui.settings.HintText
import br.com.conexus.antispam.ui.settings.MinTouch
import br.com.conexus.antispam.ui.settings.SectionGap
import br.com.conexus.antispam.ui.settings.SubScreenHeader
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.w10m.W10MButton
import br.com.conexus.antispam.ui.w10m.W10MButtonVariant
import br.com.conexus.antispam.ui.w10m.W10MDialog
import br.com.conexus.antispam.ui.w10m.W10MDialogButton
import br.com.conexus.antispam.ui.w10m.W10MDialogButtonStyle
import br.com.conexus.antispam.ui.w10m.W10MIconButton
import br.com.conexus.antispam.ui.w10m.W10MRadio
import br.com.conexus.antispam.ui.w10m.W10MScreen
import br.com.conexus.antispam.ui.w10m.W10MSection
import br.com.conexus.antispam.ui.w10m.W10MTag
import br.com.conexus.antispam.ui.w10m.W10MTagTone
import br.com.conexus.antispam.ui.w10m.W10MText
import br.com.conexus.antispam.ui.w10m.W10MTextField
import br.com.conexus.antispam.ui.w10m.W10MTextTone
import br.com.conexus.antispam.ui.w10m.W10MType

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val vm: RulesViewModel = hiltViewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    val editor = ui.editor
    W10MScreen {
        if (editor != null) {
            RuleEditorPage(editor, vm)
        } else {
            RulesListPage(items.orEmpty(), loaded = items != null, vm = vm, onBack = onBack)
        }
        val pending = ui.pendingDelete
        W10MDialog(
            visible = pending != null,
            onDismiss = vm::cancelDelete,
            title = stringResource(R.string.rules_delete_title),
            message = pending?.let { stringResource(R.string.rules_delete_message, it.name) },
            buttons = listOf(
                W10MDialogButton(stringResource(R.string.rules_delete_cancel), {}, W10MDialogButtonStyle.Cancel),
                W10MDialogButton(stringResource(R.string.rules_delete_confirm), vm::confirmDelete, W10MDialogButtonStyle.Destructive),
            ),
        )
    }
}

// ---------------------------------------------------------------- Lista

@Composable
private fun RulesListPage(items: List<RuleItem>, loaded: Boolean, vm: RulesViewModel, onBack: () -> Unit) {
    val user = items.filter { !it.isSystem }
    val system = items.filter { it.isSystem }
    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(
            title = stringResource(R.string.rules_title),
            backDescription = stringResource(R.string.lists_back),
            onBack = onBack,
            trailing = {
                W10MButton(
                    label = stringResource(R.string.rules_add),
                    variant = W10MButtonVariant.Primary,
                    onClick = vm::startNew,
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HintText(stringResource(R.string.rules_intro), Modifier.padding(horizontal = W10MSpacing.gutter))
            W10MSection(title = stringResource(R.string.rules_section_user)) {
                if (loaded && user.isEmpty()) {
                    W10MText(
                        text = stringResource(R.string.rules_empty),
                        variant = W10MType::body,
                        tone = W10MTextTone.Muted,
                        modifier = Modifier.padding(vertical = W10MSpacing.s),
                    )
                }
            }
            user.forEachIndexed { i, rule ->
                RuleRow(
                    rule = rule,
                    canUp = i > 0,
                    canDown = i < user.lastIndex,
                    onToggle = { vm.setEnabled(rule, it) },
                    onEdit = { vm.startEdit(rule) },
                    onUp = { vm.move(rule, -1) },
                    onDown = { vm.move(rule, +1) },
                )
            }
            SectionGap()
            W10MSection(title = stringResource(R.string.rules_section_system)) {
                HintText(stringResource(R.string.rules_system_hint))
            }
            system.forEach { rule ->
                RuleRow(rule = rule, canUp = false, canDown = false, onToggle = { vm.setEnabled(rule, it) }, onEdit = null, onUp = {}, onDown = {})
            }
            SectionGap()
        }
    }
}

@Composable
private fun RuleRow(
    rule: RuleItem,
    canUp: Boolean,
    canDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val palette = useW10MTheme().palette
    val summary = ruleSummary(rule)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            W10MText(text = rule.name, variant = W10MType::body, tone = if (rule.enabled) W10MTextTone.Text else W10MTextTone.Muted)
            W10MText(text = summary, variant = W10MType::caption, tone = W10MTextTone.Muted)
        }
        if (!rule.isSystem) {
            val upDesc = stringResource(R.string.rules_move_up, rule.name)
            val downDesc = stringResource(R.string.rules_move_down, rule.name)
            if (canUp) W10MIconButton(
                icon = { Icon(Icons.Default.KeyboardArrowUp, null, tint = palette.text) },
                onClick = onUp, contentDescription = upDesc, modifier = Modifier.size(MinTouch),
            ) else androidx.compose.foundation.layout.Spacer(Modifier.size(MinTouch))
            if (canDown) W10MIconButton(
                icon = { Icon(Icons.Default.KeyboardArrowDown, null, tint = palette.text) },
                onClick = onDown, contentDescription = downDesc, modifier = Modifier.size(MinTouch),
            ) else androidx.compose.foundation.layout.Spacer(Modifier.size(MinTouch))
        }
        val switchDesc = stringResource(R.string.rules_enabled_desc, rule.name)
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.accentFill,
                checkedThumbColor = palette.onAccent,
                uncheckedTrackColor = palette.border,
                uncheckedThumbColor = palette.text,
                uncheckedBorderColor = palette.border,
            ),
            modifier = Modifier.semantics { contentDescription = switchDesc },
        )
    }
}

@Composable
private fun ruleSummary(rule: RuleItem): String {
    val action = stringResource(rule.action.labelRes())
    val kind = stringResource(rule.kind.labelRes())
    return if (rule.kind == RuleKind.PREFIX || rule.kind == RuleKind.DDD) {
        stringResource(R.string.rules_summary_value, kind, rule.value, action)
    } else {
        stringResource(R.string.rules_summary, kind, action)
    }
}

private fun CallAction.labelRes() = when (this) {
    CallAction.BLOCK -> R.string.rules_action_block
    CallAction.SILENCE -> R.string.rules_action_silence
    CallAction.WARN -> R.string.rules_action_warn
    CallAction.ALLOW -> R.string.rules_action_allow
}

private fun RuleKind.labelRes() = when (this) {
    RuleKind.PREFIX -> R.string.rules_kind_prefix
    RuleKind.DDD -> R.string.rules_kind_ddd
    RuleKind.HIDDEN -> R.string.rules_kind_hidden
    RuleKind.INTERNATIONAL -> R.string.rules_kind_international
    else -> R.string.rules_kind_other
}

private fun RuleKind.descRes() = when (this) {
    RuleKind.PREFIX -> R.string.rules_kind_prefix_desc
    RuleKind.DDD -> R.string.rules_kind_ddd_desc
    RuleKind.HIDDEN -> R.string.rules_kind_hidden_desc
    RuleKind.INTERNATIONAL -> R.string.rules_kind_international_desc
    else -> R.string.rules_kind_other
}

// ---------------------------------------------------------------- Editor

@Composable
private fun RuleEditorPage(e: RuleEditor, vm: RulesViewModel) {
    val title = stringResource(if (e.isNew) R.string.rules_editor_new else R.string.rules_editor_edit)
    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(
            title = title,
            backDescription = stringResource(R.string.rules_editor_cancel),
            onBack = vm::closeEditor,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = W10MSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(W10MSpacing.s),
        ) {
            W10MTextField(
                label = stringResource(R.string.rules_field_name),
                value = e.name,
                onValueChange = vm::setName,
                errorText = if (e.error == RuleError.NAME) stringResource(R.string.rules_error_name) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            if (e.isNew) {
                W10MText(stringResource(R.string.rules_field_kind), variant = W10MType::group, tone = W10MTextTone.Accent)
                CREATABLE_KINDS.forEach { k ->
                    W10MRadio(
                        selected = e.kind == k,
                        onClick = { vm.setKind(k) },
                        label = stringResource(k.labelRes()),
                        description = stringResource(k.descRes()),
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            } else {
                W10MTag(text = stringResource(e.kind.labelRes()), tone = W10MTagTone.Outline)
            }

            if (e.needsValue) {
                val isDdd = e.kind == RuleKind.DDD
                val preview = vm.preview(e.kind, e.value)
                W10MTextField(
                    label = stringResource(if (isDdd) R.string.rules_field_ddd else R.string.rules_field_prefix),
                    value = e.value,
                    onValueChange = vm::setValue,
                    helperText = stringResource(if (isDdd) R.string.rules_field_ddd_hint else R.string.rules_field_prefix_hint),
                    errorText = if (e.error == RuleError.VALUE) {
                        stringResource(if (isDdd) R.string.rules_error_ddd else R.string.rules_error_prefix)
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (preview != null) {
                    W10MText(
                        text = stringResource(if (isDdd) R.string.rules_will_match_ddd else R.string.rules_will_match, preview),
                        variant = W10MType::caption,
                        tone = W10MTextTone.Muted,
                    )
                }
            }

            W10MText(stringResource(R.string.rules_field_action), variant = W10MType::group, tone = W10MTextTone.Accent)
            RULE_ACTIONS.forEach { a ->
                W10MRadio(
                    selected = e.action == a,
                    onClick = { vm.setAction(a) },
                    label = stringResource(a.labelRes()),
                    modifier = Modifier.heightIn(min = MinTouch),
                )
            }

            if (e.error == RuleError.GENERIC) {
                W10MText(stringResource(R.string.rules_error_generic), variant = W10MType::body, tone = W10MTextTone.Danger)
            }

            W10MButton(
                label = stringResource(R.string.rules_editor_save),
                variant = W10MButtonVariant.Primary,
                onClick = vm::save,
                block = true,
                modifier = Modifier.heightIn(min = MinTouch),
            )
            if (!e.isNew) {
                val rule = (vm.items.value.orEmpty()).firstOrNull { it.id == e.id }
                if (rule != null && !rule.isSystem) {
                    W10MButton(
                        label = stringResource(R.string.rules_editor_delete),
                        variant = W10MButtonVariant.Danger,
                        onClick = { vm.askDelete(rule) },
                        block = true,
                        modifier = Modifier.heightIn(min = MinTouch),
                    )
                }
            }
            SectionGap()
        }
    }
}
