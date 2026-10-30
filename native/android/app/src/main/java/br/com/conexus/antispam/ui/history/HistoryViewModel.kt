package br.com.conexus.antispam.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.ads.AdsManager
import br.com.conexus.antispam.domain.Clock
import br.com.conexus.antispam.domain.HistoryItem
import br.com.conexus.antispam.domain.HistoryRepository
import br.com.conexus.antispam.domain.ListRepository
import br.com.conexus.antispam.domain.ListStatus
import br.com.conexus.antispam.domain.ReportCategory
import br.com.conexus.antispam.domain.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lista (null enquanto carrega) do filtro atual. */
data class HistoryListState(
    val tab: HistoryTab = HistoryTab.ALL,
    /** null = ainda carregando a primeira vez. */
    val items: List<HistoryItem>? = null,
)

/** Detalhe aberto: o item (null = foi apagado) e em que lista o número está. */
data class HistoryDetailState(
    val item: HistoryItem?,
    val listStatus: ListStatus,
    val actionable: Boolean,
)

enum class HistoryConfirm { CLEAR, DELETE }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val lists: ListRepository,
    reports: ReportRepository,
    val adsManager: AdsManager,
    private val clock: Clock,
) : ViewModel() {

    private val actions = ListActions(lists, reports)

    private val tab = MutableStateFlow(HistoryTab.ALL)
    private val selectedId = MutableStateFlow<String?>(null)

    val list: StateFlow<HistoryListState> = tab
        .flatMapLatest { t ->
            history.observe(t.source).map { HistoryListState(t, t.apply(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryListState())

    val detail: StateFlow<HistoryDetailState?> = selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else combine(history.observeItem(id), lists.observeBlocked(), lists.observeAllowed()) { item, _, _ ->
                HistoryDetailState(
                    item = item,
                    listStatus = item?.let { lists.status(it.number) } ?: ListStatus.NONE,
                    actionable = item?.let { actions.isActionable(it.number) } ?: false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    private val _confirm = MutableStateFlow<HistoryConfirm?>(null)
    val confirm: StateFlow<HistoryConfirm?> = _confirm

    private val _reporting = MutableStateFlow(false)
    /** Seletor de categoria da denúncia aberto. */
    val reporting: StateFlow<Boolean> = _reporting

    fun nowMillis(): Long = clock.nowMillis()

    fun selectTab(t: HistoryTab) { tab.value = t }
    fun open(id: String) { selectedId.value = id; _notice.value = null }
    fun closeDetail() { selectedId.value = null; _reporting.value = false; _confirm.value = null }
    fun dismissNotice() { _notice.value = null }

    fun askClear() { _confirm.value = HistoryConfirm.CLEAR }
    fun askDelete() { _confirm.value = HistoryConfirm.DELETE }
    fun cancelConfirm() { _confirm.value = null }
    fun askReport() { _reporting.value = true }
    fun cancelReport() { _reporting.value = false }

    fun block(item: HistoryItem) = launchNotice { actions.block(item.number) }

    fun allow(item: HistoryItem, wasBlocked: Boolean) = launchNotice { actions.allow(item.number, wasBlocked) }

    fun report(item: HistoryItem, category: ReportCategory) {
        _reporting.value = false
        launchNotice { actions.report(item.number, category) }
    }

    fun undo() {
        val undo = _notice.value?.undo ?: return
        launchNotice { actions.undo(undo) }
    }

    /** Confirmada: apaga o item aberto (e volta à lista) ou todo o histórico. */
    fun confirmDestructive() {
        val what = _confirm.value ?: return
        _confirm.value = null
        viewModelScope.launch {
            when (what) {
                HistoryConfirm.CLEAR -> {
                    history.clear()
                    selectedId.value = null
                    _notice.value = Notice(NoticeKind.CLEARED)
                }
                HistoryConfirm.DELETE -> {
                    val id = selectedId.value ?: return@launch
                    history.delete(id)
                    selectedId.value = null
                    _notice.value = Notice(NoticeKind.DELETED)
                }
            }
        }
    }

    private fun launchNotice(block: suspend () -> Notice) {
        viewModelScope.launch { _notice.value = block() }
    }
}
