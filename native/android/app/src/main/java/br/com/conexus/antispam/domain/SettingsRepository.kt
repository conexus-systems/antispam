package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.DatasetMetaDao
import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.SystemRules
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.database.UserSettingsDao
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.ProtectionMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ajustes persistentes (RF-AJ-01). Cada setter grava imediatamente.
 *
 * Três opções são espelhadas nas regras de sistema, que são o que o motor realmente lê:
 * [setHandle0303] (regra 0303), [setBlockHidden] e [setBlockInternational].
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dao: UserSettingsDao,
    private val ruleDao: RuleDao,
    private val metaDao: DatasetMetaDao,
) {
    private val mutex = Mutex()

    /** Sempre emite um valor (padrão se a linha ainda não existir). */
    fun observe(): Flow<UserSettings> = dao.observe().map { it ?: UserSettings() }

    suspend fun get(): UserSettings = dao.getSync() ?: UserSettings()

    /** "Base local: versão X, de <data>". null até a semente rodar. */
    fun observeDataset(): Flow<DatasetInfo?> = metaDao.observe().map { m ->
        m?.let { DatasetInfo(it.version, it.publishedAt, IsoTime.parse(it.installedAt), it.entries, it.source, it.signatureOk) }
    }

    /** Leitura-modificação-escrita atômica dentro do processo. Use para ajustes sem setter dedicado. */
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        mutex.withLock { dao.upsert(transform(get()).copy(id = 1)) }
    }

    suspend fun setMode(mode: ProtectionMode) = update { it.copy(mode = mode.name) }

    /** Limiares do modo CUSTOM. Força warn <= block <= silence (0..101). */
    suspend fun setCustomThresholds(warn: Int, block: Int, silence: Int) = update {
        val w = warn.coerceIn(0, 100)
        val b = block.coerceIn(w, 100)
        val s = silence.coerceIn(b, 101)
        it.copy(warnThreshold = w, blockThreshold = b, silenceThreshold = s)
    }

    /** value: "allow", "identify", "silence" ou "block". Atualiza também a regra de sistema 0303. */
    suspend fun setHandle0303(value: String) {
        require(value in HANDLE_0303) { "handle0303 inválido: $value" }
        update { it.copy(handle0303 = value) }
        when (value) {
            "allow" -> ruleDao.setEnabled(SystemRules.ZERO_THREE_ZERO_ID, false)
            "identify" -> { ruleDao.setAction(SystemRules.ZERO_THREE_ZERO_ID, CallAction.WARN.name); ruleDao.setEnabled(SystemRules.ZERO_THREE_ZERO_ID, true) }
            "silence" -> { ruleDao.setAction(SystemRules.ZERO_THREE_ZERO_ID, CallAction.SILENCE.name); ruleDao.setEnabled(SystemRules.ZERO_THREE_ZERO_ID, true) }
            "block" -> { ruleDao.setAction(SystemRules.ZERO_THREE_ZERO_ID, CallAction.BLOCK.name); ruleDao.setEnabled(SystemRules.ZERO_THREE_ZERO_ID, true) }
        }
    }

    /** Liga/desliga a regra de sistema "Números ocultos" (ação BLOCK). */
    suspend fun setBlockHidden(enabled: Boolean) {
        update { it.copy(blockHidden = enabled) }
        ruleDao.setEnabled(SystemRules.HIDDEN_ID, enabled)
    }

    /** Liga/desliga a regra de sistema "Números internacionais" (ação BLOCK). */
    suspend fun setBlockInternational(enabled: Boolean) {
        update { it.copy(blockInternational = enabled) }
        ruleDao.setEnabled(SystemRules.INTERNATIONAL_ID, enabled)
    }

    suspend fun setBlockUnknownInAggressive(v: Boolean) = update { it.copy(blockUnknownInAggressive = v) }
    suspend fun setNotificationsEnabled(v: Boolean) = update { it.copy(notificationsEnabled = v) }
    suspend fun setRejectBlockedCalls(v: Boolean) = update { it.copy(rejectBlockedCalls = v) }
    suspend fun setTheme(theme: String) = update { it.copy(theme = theme) }
    suspend fun setAccent(hex: String) = update { it.copy(accent = hex) }
    suspend fun setReduceMotion(v: Boolean) = update { it.copy(reduceMotion = v) }
    suspend fun setOnboardingDone(v: Boolean) = update { it.copy(onboardingDone = v) }
    suspend fun setHistoryRetentionDays(days: Int) = update { it.copy(historyRetentionDays = days.coerceIn(1, 3650)) }
    suspend fun setConsentShareReports(v: Boolean) = update { it.copy(consentShareReports = v) }

    companion object {
        val HANDLE_0303 = setOf("allow", "identify", "silence", "block")
    }
}
