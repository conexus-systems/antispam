package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.BlocklistDao
import br.com.conexus.antispam.database.DatasetMetaDao
import br.com.conexus.antispam.database.DefaultData
import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.UserSettingsDao
import br.com.conexus.antispam.sync.DatasetSeeder
import javax.inject.Inject
import javax.inject.Singleton

/** Limpa preferências locais fora do Room (DataStore: onboarding etc.). Ligado em DomainModule. */
fun interface LocalPrefsCleaner {
    suspend fun clear()
}

/** RF-PRIV-01 / T19: "Apagar todos os meus dados". */
@Singleton
class PrivacyRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val blocklistDao: BlocklistDao,
    private val reputationDao: ReputationDao,
    private val ruleDao: RuleDao,
    private val settingsDao: UserSettingsDao,
    private val metaDao: DatasetMetaDao,
    private val prefsCleaner: LocalPrefsCleaner,
    private val clock: Clock,
) {
    /**
     * Apaga histórico, listas, reputação, regras e ajustes, e recria os padrões
     * (configurações, regras de sistema, semente embutida). Depois disso o app volta ao estado inicial
     * (onboarding). Não faz rede.
     */
    suspend fun eraseAllLocalData() {
        historyDao.clear()
        blocklistDao.clear()
        reputationDao.clear()
        ruleDao.clear()
        metaDao.clear()
        settingsDao.clear()
        prefsCleaner.clear()

        val now = clock.nowMillis()
        settingsDao.upsert(DefaultData.settings())
        metaDao.upsert(DefaultData.meta(now))
        DefaultData.rules(now).forEach { ruleDao.upsert(it) }
        DatasetSeeder(reputationDao, metaDao, settingsDao).applyEmbedded(now)
    }
}
