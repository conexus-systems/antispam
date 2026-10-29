package br.com.conexus.antispam.ui.onboarding

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resultado do pedido do papel de triagem; qualquer um deles deixa o app utilizável. */
enum class RoleOutcome { NOT_ASKED, GRANTED, DENIED, UNAVAILABLE }

/**
 * Onboarding (RF-PROT-01): divulgação em destaque, papel de triagem, notificações em contexto.
 * Os pedidos de sistema (papel e POST_NOTIFICATIONS) são feitos pela tela com ActivityResultLauncher;
 * aqui só ficam o passo atual e o resultado. "Concluído" mora em UserSettings.onboardingDone, então
 * "Apagar todos os meus dados" (que recria os padrões) devolve o usuário ao onboarding.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 0 = divulgação, 1 = papel de triagem, 2 = notificações (só Android 13+). */
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _role = MutableStateFlow(RoleOutcome.NOT_ASKED)
    val role: StateFlow<RoleOutcome> = _role.asStateFlow()

    val stepCount: Int = stepCountFor(Build.VERSION.SDK_INT)

    fun next() {
        if (_step.value < stepCount - 1) _step.value += 1
    }

    fun back() {
        if (_step.value > 0) _step.value -= 1
    }

    /** [held] é o estado do papel relido ao voltar do diálogo; [available] = o aparelho oferece o papel. */
    fun onRoleResult(held: Boolean, available: Boolean) {
        _role.value = when {
            held -> RoleOutcome.GRANTED
            !available -> RoleOutcome.UNAVAILABLE
            else -> RoleOutcome.DENIED
        }
    }

    /** Marca o onboarding como feito; quem observa a configuração leva o usuário à Home. */
    fun finish() {
        viewModelScope.launch { settings.setOnboardingDone(true) }
    }

    companion object {
        fun stepCountFor(sdk: Int): Int = if (sdk >= Build.VERSION_CODES.TIRAMISU) 3 else 2
    }
}
