package br.com.conexus.antispam.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.domain.PrivacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EraseStatus { IDLE, ERASING, DONE, FAILED }

data class PrivacyUiState(val confirming: Boolean = false, val status: EraseStatus = EraseStatus.IDLE)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val privacy: PrivacyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PrivacyUiState())
    val ui: StateFlow<PrivacyUiState> = _ui.asStateFlow()

    fun askErase() = _ui.update { it.copy(confirming = true, status = EraseStatus.IDLE) }
    fun cancelErase() = _ui.update { it.copy(confirming = false) }

    /**
     * Apaga tudo. Os ajustes voltam ao padrão com onboardingDone = false, então quem observa os
     * ajustes (navegação raiz) leva o usuário de volta ao onboarding.
     */
    fun confirmErase() {
        if (_ui.value.status == EraseStatus.ERASING) return
        _ui.update { it.copy(confirming = false, status = EraseStatus.ERASING) }
        viewModelScope.launch {
            val ok = runCatching { privacy.eraseAllLocalData() }.isSuccess
            _ui.update { it.copy(status = if (ok) EraseStatus.DONE else EraseStatus.FAILED) }
        }
    }
}
