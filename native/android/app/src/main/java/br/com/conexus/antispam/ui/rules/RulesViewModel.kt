package br.com.conexus.antispam.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.database.SystemRules
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.domain.RuleItem
import br.com.conexus.antispam.domain.RuleKind
import br.com.conexus.antispam.domain.RuleResult
import br.com.conexus.antispam.domain.RulesRepository
import br.com.conexus.antispam.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipos que o usuário pode criar. NOT_CONTACT fica de fora: o MVP não lê contatos (sem READ_CONTACTS). */
val CREATABLE_KINDS = listOf(RuleKind.PREFIX, RuleKind.DDD, RuleKind.HIDDEN, RuleKind.INTERNATIONAL)

/** Ações oferecidas, da mais para a menos restritiva. */
val RULE_ACTIONS = listOf(CallAction.BLOCK, CallAction.SILENCE, CallAction.WARN, CallAction.ALLOW)

enum class RuleError { NAME, VALUE, GENERIC }

data class RuleEditor(
    /** null = nova regra. */
    val id: Long? = null,
    val kind: RuleKind = RuleKind.PREFIX,
    val name: String = "",
    val value: String = "",
    val action: CallAction = CallAction.BLOCK,
    val error: RuleError? = null,
) {
    val isNew: Boolean get() = id == null
    val needsValue: Boolean get() = kind == RuleKind.PREFIX || kind == RuleKind.DDD
}

data class RulesUiState(
    val editor: RuleEditor? = null,
    val pendingDelete: RuleItem? = null,
)

/** Remove o que não pode estar num prefixo/DDD digitado: só dígitos, '+' inicial e separadores comuns. */
fun sanitizeValue(kind: RuleKind, raw: String): String = when (kind) {
    RuleKind.DDD -> raw.filter { it.isDigit() }.take(2)
    RuleKind.PREFIX -> {
        val allowed = raw.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' }
        // '+' só é aceito na primeira posição
        (allowed.take(1) + allowed.drop(1).replace("+", "")).take(20)
    }
    else -> ""
}

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val rules: RulesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val items: StateFlow<List<RuleItem>?> =
        rules.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ui = MutableStateFlow(RulesUiState())
    val ui: StateFlow<RulesUiState> = _ui.asStateFlow()

    // ---- lista

    /**
     * Regras de sistema passam pelo [SettingsRepository] para o interruptor de Ajustes e a regra
     * continuarem coerentes. Ligar o 0303 volta para a ação da regra (ou "silenciar").
     */
    fun setEnabled(rule: RuleItem, enabled: Boolean) {
        viewModelScope.launch {
            when (rule.id) {
                SystemRules.ZERO_THREE_ZERO_ID -> settings.setHandle0303(
                    if (!enabled) "allow" else when (rule.action) {
                        CallAction.BLOCK -> "block"
                        CallAction.WARN -> "identify"
                        else -> "silence"
                    },
                )
                SystemRules.HIDDEN_ID -> settings.setBlockHidden(enabled)
                SystemRules.INTERNATIONAL_ID -> settings.setBlockInternational(enabled)
                else -> rules.setEnabled(rule.id, enabled)
            }
        }
    }

    fun move(rule: RuleItem, delta: Int) {
        val ids = items.value.orEmpty().filter { !it.isSystem }.map { it.id }.toMutableList()
        val from = ids.indexOf(rule.id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { rules.reorder(ids) }
    }

    // ---- editor

    fun startNew() = _ui.update { it.copy(editor = RuleEditor()) }

    fun startEdit(rule: RuleItem) {
        if (rule.isSystem) return
        _ui.update { it.copy(editor = RuleEditor(rule.id, rule.kind, rule.name, rule.value, rule.action)) }
    }

    fun closeEditor() = _ui.update { it.copy(editor = null) }

    fun setName(v: String) = editEditor { it.copy(name = v.take(40), error = null) }
    fun setValue(v: String) = editEditor { it.copy(value = sanitizeValue(it.kind, v), error = null) }
    fun setAction(a: CallAction) = editEditor { it.copy(action = a) }
    fun setKind(k: RuleKind) = editEditor { if (it.isNew) it.copy(kind = k, value = "", error = null) else it }

    /** Valor como será gravado (prévia para o usuário), ou null se ainda inválido. */
    fun preview(kind: RuleKind, value: String): String? = rules.normalizeValue(kind, value)?.takeIf { it.isNotEmpty() }

    fun save() {
        val e = _ui.value.editor ?: return
        if (e.name.isBlank()) return editEditor { it.copy(error = RuleError.NAME) }
        if (e.needsValue && rules.normalizeValue(e.kind, e.value) == null) {
            return editEditor { it.copy(error = RuleError.VALUE) }
        }
        viewModelScope.launch {
            val result = if (e.id == null) rules.create(e.name, e.kind, e.value, e.action)
            else rules.update(e.id, e.name, e.value, e.action)
            when (result) {
                is RuleResult.Saved -> closeEditor()
                is RuleResult.Invalid -> editEditor { it.copy(error = RuleError.GENERIC) }
            }
        }
    }

    fun askDelete(rule: RuleItem) {
        if (rule.isSystem) return
        _ui.update { it.copy(pendingDelete = rule) }
    }

    fun cancelDelete() = _ui.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val rule = _ui.value.pendingDelete ?: return
        viewModelScope.launch {
            rules.delete(rule.id)
            _ui.update { it.copy(pendingDelete = null, editor = null) }
        }
    }

    private fun editEditor(f: (RuleEditor) -> RuleEditor) = _ui.update { s -> s.copy(editor = s.editor?.let(f)) }
}
