package br.com.conexus.antispam.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.conexus.antispam.ads.AdsManager
import br.com.conexus.antispam.ads.AdsState
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.domain.DatasetInfo
import br.com.conexus.antispam.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Valida "#RRGGBB" (o repositório guarda o texto como veio, então a UI garante o formato). */
object AccentColor {
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    fun normalize(raw: String): String? {
        val t = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
        return t.takeIf { HEX.matches(it) }?.uppercase()
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    val adsManager: AdsManager,
) : ViewModel() {

    /** null até a primeira leitura do Room (a tela mostra um estado neutro, sem piscar valores falsos). */
    val settings: StateFlow<UserSettings?> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dataset: StateFlow<DatasetInfo?> =
        repo.observeDataset().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val adsState: StateFlow<AdsState> = adsManager.state

    fun setMode(mode: ProtectionMode) = launch { repo.setMode(mode) }

    fun setThresholds(warn: Int, block: Int, silence: Int) = launch { repo.setCustomThresholds(warn, block, silence) }

    fun setHandle0303(value: String) = launch { repo.setHandle0303(value) }
    fun setBlockHidden(v: Boolean) = launch { repo.setBlockHidden(v) }
    fun setBlockInternational(v: Boolean) = launch { repo.setBlockInternational(v) }
    fun setBlockUnknownInAggressive(v: Boolean) = launch { repo.setBlockUnknownInAggressive(v) }
    fun setRejectBlockedCalls(v: Boolean) = launch { repo.setRejectBlockedCalls(v) }
    fun setNotificationsEnabled(v: Boolean) = launch { repo.setNotificationsEnabled(v) }
    fun setTheme(theme: String) = launch { repo.setTheme(theme) }
    fun setReduceMotion(v: Boolean) = launch { repo.setReduceMotion(v) }

    /** @return false se o texto não for uma cor #RRGGBB válida (nada é gravado). */
    fun setAccent(raw: String): Boolean {
        val hex = AccentColor.normalize(raw) ?: return false
        launch { repo.setAccent(hex) }
        return true
    }

    fun resetAccent() = launch { repo.setAccent(UserSettings().accent) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
