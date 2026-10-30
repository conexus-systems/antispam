package br.com.conexus.antispam.ui.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.ads.AdsManager
import br.com.conexus.antispam.ads.AdsState
import br.com.conexus.antispam.ads.InterstitialController
import br.com.conexus.antispam.domain.ListRepository
import br.com.conexus.antispam.domain.ListStatus
import br.com.conexus.antispam.domain.ReportCategory
import br.com.conexus.antispam.domain.ReportRepository
import br.com.conexus.antispam.domain.ScreeningResult
import br.com.conexus.antispam.domain.VerifyNumberUseCase
import br.com.conexus.antispam.domain.VerifyOutcome
import br.com.conexus.antispam.ui.history.ListActions
import br.com.conexus.antispam.ui.history.Notice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifyUiState(
    val input: String = "",
    val loading: Boolean = false,
    /** Mensagem de VerifyOutcome.Invalid (ex.: "Número inválido"); nunca junto com [result]. */
    val error: String? = null,
    val result: ScreeningResult? = null,
    /** Muda a cada verificação concluída com sucesso (dispara o intersticial depois do resultado). */
    val completedCount: Int = 0,
    val actionable: Boolean = false,
    val reporting: Boolean = false,
    val notice: Notice? = null,
)

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val verify: VerifyNumberUseCase,
    private val lists: ListRepository,
    reports: ReportRepository,
    private val ads: AdsManager,
    private val interstitial: InterstitialController,
) : ViewModel() {

    private val actions = ListActions(lists, reports)
    private val _state = MutableStateFlow(VerifyUiState())
    val state: StateFlow<VerifyUiState> = _state.asStateFlow()

    val adsState: StateFlow<AdsState> get() = ads.state

    fun onInput(text: String) = _state.update { it.copy(input = text, error = null) }

    fun check() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(loading = true, error = null, notice = null, reporting = false) }
        viewModelScope.launch {
            when (val outcome = verify(s.input)) {
                is VerifyOutcome.Invalid -> _state.update {
                    it.copy(loading = false, error = outcome.message, result = null, actionable = false)
                }
                is VerifyOutcome.Ready -> _state.update {
                    it.copy(
                        loading = false,
                        result = outcome.result,
                        actionable = actions.isActionable(outcome.result.number),
                        completedCount = it.completedCount + 1,
                    )
                }
            }
        }
    }

    fun block() = act { r -> actions.block(r.number) }

    fun allow() = act { r -> actions.allow(r.number, wasBlocked = r.listStatus == ListStatus.BLOCKED) }

    fun askReport() = _state.update { it.copy(reporting = true) }

    fun cancelReport() = _state.update { it.copy(reporting = false) }

    fun report(category: ReportCategory) {
        _state.update { it.copy(reporting = false) }
        act { r -> actions.report(r.number, category) }
    }

    fun undo() {
        val undo = _state.value.notice?.undo ?: return
        act { _ -> actions.undo(undo) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun preloadAd() = interstitial.preload()

    /** Chamado pela tela já com o resultado visível e sem ação em andamento. */
    fun onResultShown(activity: android.app.Activity) = interstitial.onVerificationCompleted(activity)

    private fun act(block: suspend (ScreeningResult) -> Notice) {
        val r = _state.value.result ?: return
        viewModelScope.launch {
            val notice = block(r)
            val status = lists.status(r.number)
            _state.update { it.copy(notice = notice, result = it.result?.copy(listStatus = status)) }
        }
    }
}
