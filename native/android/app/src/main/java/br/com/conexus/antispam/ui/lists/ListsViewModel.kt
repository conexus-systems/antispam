package br.com.conexus.antispam.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.database.BlocklistEntry
import br.com.conexus.antispam.decision.PhoneNormalizer
import br.com.conexus.antispam.domain.ListItem
import br.com.conexus.antispam.domain.PhoneFormat
import br.com.conexus.antispam.domain.ListKind
import br.com.conexus.antispam.domain.ListRepository
import br.com.conexus.antispam.domain.ListResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que desfazer na barra "Desfazer". */
sealed interface ListUndo {
    data class Add(val added: ListResult.Added) : ListUndo
    data class Remove(val entry: BlocklistEntry) : ListUndo
}

enum class ListFeedbackKind(val isError: Boolean) {
    INVALID(true), DUPLICATE(true), EMERGENCY(true), BLOCKED(false), ALLOWED(false), REMOVED(false),
}

/** Mensagem de retorno; o texto em PT-BR vem de string resources na tela. [display] = número formatado. */
data class ListFeedback(val kind: ListFeedbackKind, val display: String = "", val undo: ListUndo? = null)

data class ListsUiState(
    val tab: ListKind = ListKind.BLOCKED,
    val number: String = "",
    val note: String = "",
    val feedback: ListFeedback? = null,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val lists: ListRepository,
) : ViewModel() {

    val blocked: StateFlow<List<ListItem>?> =
        lists.observeBlocked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val allowed: StateFlow<List<ListItem>?> =
        lists.observeAllowed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ui = MutableStateFlow(ListsUiState())
    val ui: StateFlow<ListsUiState> = _ui.asStateFlow()

    fun selectTab(tab: ListKind) = _ui.update { it.copy(tab = tab, feedback = null) }
    fun setNumber(v: String) = _ui.update { it.copy(number = v.take(MAX_NUMBER), feedback = null) }
    fun setNote(v: String) = _ui.update { it.copy(note = v.take(MAX_NOTE)) }
    fun dismissFeedback() = _ui.update { it.copy(feedback = null) }

    /** Adiciona o número digitado à lista da aba atual. */
    fun addTyped() {
        val s = _ui.value
        val note = s.note.trim().ifEmpty { null }
        viewModelScope.launch {
            val result = if (s.tab == ListKind.BLOCKED) lists.block(s.number, note) else lists.allow(s.number, note)
            val fb = feedbackFor(result, s.tab)
            _ui.update { cur ->
                if (result is ListResult.Added) cur.copy(number = "", note = "", feedback = fb) else cur.copy(feedback = fb)
            }
        }
    }

    /** "Desbloquear" (bloqueado -> permitido) ou "Bloquear" (permitido -> bloqueado). */
    fun moveToOther(item: ListItem, from: ListKind) {
        viewModelScope.launch {
            val result = if (from == ListKind.BLOCKED) lists.allow(item.number) else lists.block(item.number)
            _ui.update { it.copy(feedback = feedbackFor(result, if (from == ListKind.BLOCKED) ListKind.ALLOWED else ListKind.BLOCKED)) }
        }
    }

    fun remove(item: ListItem) {
        viewModelScope.launch {
            val entry = lists.get(item.number)
            lists.remove(item.number)
            _ui.update {
                it.copy(
                    feedback = ListFeedback(
                        ListFeedbackKind.REMOVED, item.display,
                        undo = entry?.let { e -> ListUndo.Remove(e) },
                    ),
                )
            }
        }
    }

    fun undo() {
        val undo = _ui.value.feedback?.undo ?: return
        viewModelScope.launch {
            when (undo) {
                is ListUndo.Add -> lists.undo(undo.added)
                is ListUndo.Remove -> lists.restore(undo.entry)
            }
            _ui.update { it.copy(feedback = null) }
        }
    }

    private fun feedbackFor(result: ListResult, tab: ListKind): ListFeedback = when (result) {
        is ListResult.Added -> ListFeedback(
            if (tab == ListKind.BLOCKED) ListFeedbackKind.BLOCKED else ListFeedbackKind.ALLOWED,
            display = displayOf(result.entry.number),
            undo = ListUndo.Add(result),
        )
        ListResult.Invalid -> ListFeedback(ListFeedbackKind.INVALID)
        ListResult.Duplicate -> ListFeedback(ListFeedbackKind.DUPLICATE)
        ListResult.Emergency -> ListFeedback(ListFeedbackKind.EMERGENCY)
    }

    private fun displayOf(e164: String): String = PhoneFormat.display(PhoneNormalizer.normalize(e164))

    companion object {
        const val MAX_NUMBER = 24
        const val MAX_NOTE = 60
    }
}
