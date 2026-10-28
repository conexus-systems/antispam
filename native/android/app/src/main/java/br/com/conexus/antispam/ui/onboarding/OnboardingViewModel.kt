package br.com.conexus.antispam.ui.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _activating = MutableStateFlow(false)
    val activating = _activating.asStateFlow()

    private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

    val onboardingDone: Flow<Boolean> = dataStore.data
        .map { it[ONBOARDING_DONE_KEY] ?: false }

    fun nextStep() {
        _currentStep.value += 1
    }

    fun activateProtection(activity: Activity) {
        _activating.value = true
        viewModelScope.launch {
            try {
                if (isNativeScreeningAvailable()) {
                    val held = isCallScreeningRoleHeld()
                    if (!held) {
                        requestCallScreeningRole(activity)
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
            dataStore.edit { it[ONBOARDING_DONE_KEY] = true }
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

    private fun requestCallScreeningRole(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                activity.startActivityForResult(intent, 4711)
            }
        }
    }
}