package br.com.conexus.antispam.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.Clock
import br.com.conexus.antispam.domain.HistoryCounts
import br.com.conexus.antispam.domain.HistoryFilter
import br.com.conexus.antispam.domain.HistoryItem
import br.com.conexus.antispam.domain.HistoryRepository
import br.com.conexus.antispam.domain.ScreeningResult
import br.com.conexus.antispam.domain.SettingsRepository
import br.com.conexus.antispam.domain.VerifyNumberUseCase
import br.com.conexus.antispam.domain.VerifyOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado do simulador: nunca gravado, sempre marcado como simulação na UI. */
data class SimulatorState(
    val input: String = "",
    val running: Boolean = false,
    val result: ScreeningResult? = null,
    /** Mensagem PT-BR de validação ("Número inválido", "Inclua o DDD"...). */
    val error: String? = null,
)

data class HomeUiState(
    val loaded: Boolean = false,
    val roleHeld: Boolean = false,
    val roleAvailable: Boolean = true,
    val notificationsGranted: Boolean = true,
    val notificationsWanted: Boolean = true,
    val mode: ProtectionMode = ProtectionMode.BALANCED,
    val today: HistoryCounts = HistoryCounts(0, 0, 0, 0),
    val total: Int = 0,
    val recent: List<HistoryItem> = emptyList(),
) {
    val protection: ProtectionState get() = protectionState(roleHeld, mode)
    val showNotificationsWarning: Boolean get() = notificationsWanted && !notificationsGranted
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    history: HistoryRepository,
    private val verify: VerifyNumberUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** Estado que só o Android responde (papel e permissão): relido em onResume. */
    private data class System(val roleHeld: Boolean, val roleAvailable: Boolean, val notifications: Boolean)

    private val system = MutableStateFlow(readSystem())

    val ui: StateFlow<HomeUiState> = combine(
        settings.observe(),
        history.observeCounts(),
        history.observe(HistoryFilter.ALL, limit = RECENT_WINDOW),
        system,
    ) { s, counts, items, sys ->
        HomeUiState(
            loaded = true,
            roleHeld = sys.roleHeld,
            roleAvailable = sys.roleAvailable,
            notificationsGranted = sys.notifications,
            notificationsWanted = s.notificationsEnabled,
            mode = parseMode(s.mode),
            today = countsSince(items, startOfDayMillis(clock.nowMillis())),
            total = counts.total,
            recent = items.take(RECENT_SHOWN),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _sim = MutableStateFlow(SimulatorState())
    val simulator: StateFlow<SimulatorState> = _sim.asStateFlow()

    /** Chamar em onResume e depois de qualquer pedido de permissão ou papel. */
    fun refreshSystem() {
        system.value = readSystem()
    }

    fun onSimInput(text: String) {
        _sim.value = _sim.value.copy(input = text, error = null)
    }

    fun clearSimulation() {
        _sim.value = _sim.value.copy(result = null, error = null)
    }

    /** [hidden] = simula "número oculto" (apresentação restrita), ignorando o campo. */
    fun simulate(hidden: Boolean) {
        if (_sim.value.running) return
        val raw = _sim.value.input
        _sim.value = _sim.value.copy(running = true, error = null, result = null)
        viewModelScope.launch {
            val presentation = if (hidden) CallPresentation.RESTRICTED else CallPresentation.UNKNOWN
            val outcome = runCatching { verify(if (hidden) "" else raw, presentation) }.getOrNull()
            _sim.value = when (outcome) {
                is VerifyOutcome.Ready -> _sim.value.copy(running = false, result = outcome.result)
                is VerifyOutcome.Invalid -> _sim.value.copy(running = false, error = outcome.message)
                null -> _sim.value.copy(running = false, error = SIM_FAILED)
            }
        }
    }

    private fun readSystem() = System(
        roleHeld = ProtectionRole.isHeld(context),
        roleAvailable = ProtectionRole.isAvailable(context),
        notifications = ProtectionRole.notificationsGranted(context),
    )

    companion object {
        /** Janela lida do histórico para os contadores do dia. */
        const val RECENT_WINDOW = 500
        const val RECENT_SHOWN = 5
        const val SIM_FAILED = "Não foi possível simular agora. Tente de novo."
    }
}
