package br.com.conexus.antispam.ui.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.rx.RxDataStore
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.reactivex.rxjava3.core.Completable
import javax.inject.Inject

@dagger.hilt.android.lifecycle.HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val context: Context,
    private val dataStore: RxDataStore
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _activating = MutableStateFlow(false)
    val activating = _activating.asStateFlow()

    private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

    val onboardingDone = dataStore.data
        .map { it[ONBOARDING_DONE_KEY] ?: false }
        .toFlowable()

    fun nextStep() {
        _currentStep.update { it + 1 }
    }

    fun activateProtection() {
        _activating.value = true
        viewModelScope.launch {
            try {
                if (isNativeScreeningAvailable()) {
                    val held = isCallScreeningRoleHeld()
                    if (!held) {
                        requestCallScreeningRole()
                    }
                } else {
                    // Modo demonstração - sem módulo nativo
                }
            } finally {
                _activating.value = false
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            dataStore.updateDataAsync { preferences ->
                preferences.toMutablePreferences().apply {
                    putBoolean(ONBOARDING_DONE_KEY, true)
                }
            }.await()
        }
    }

    private fun isNativeScreeningAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun isCallScreeningRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestCallScreeningRole() {
        val activity = context as? Activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                activity.startActivityForResult(intent, 4711)
            }
        }
    }
}