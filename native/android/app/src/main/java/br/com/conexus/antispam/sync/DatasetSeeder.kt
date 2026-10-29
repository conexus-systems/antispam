package br.com.conexus.antispam.sync

import br.com.conexus.antispam.database.DatasetMeta
import br.com.conexus.antispam.database.DatasetMetaDao
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.ReputationEntry
import br.com.conexus.antispam.database.UserSettingsDao
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.domain.IsoTime

/**
 * Semente embutida no código (RF-OFF-01): funciona sem rede e sem asset.
 * Não contém números de pessoas físicas. Hoje é vazia: a v1 funciona com listas, regras e padrões
 * (docs/sdd/app-functional-spec.md §11, decisão 3). Para publicar dados, preencha [ENTRIES] e suba [VERSION].
 */
object EmbeddedSeed {
    const val VERSION = 1L
    const val PUBLISHED_AT = "2026-10-28T00:00:00.000Z"
    val ENTRIES: List<ReputationEntry> = emptyList()
}

class DatasetSeeder(
    private val reputationDao: ReputationDao,
    private val metaDao: DatasetMetaDao,
    private val settingsDao: UserSettingsDao,
) {
    /** Aplica a semente embutida e registra dataset_meta (source = seed) + datasetVersion. */
    suspend fun applyEmbedded(nowMillis: Long = System.currentTimeMillis()) {
        if (EmbeddedSeed.ENTRIES.isNotEmpty()) reputationDao.upsertAll(EmbeddedSeed.ENTRIES)
        record(EmbeddedSeed.VERSION, EmbeddedSeed.PUBLISHED_AT, EmbeddedSeed.ENTRIES.size, signatureOk = true, nowMillis)
    }

    /** Registra uma semente vinda de asset assinado, já verificada. */
    suspend fun record(version: Long, publishedAt: String, entries: Int, signatureOk: Boolean, nowMillis: Long) {
        metaDao.upsert(
            DatasetMeta(
                id = 1,
                version = version,
                publishedAt = publishedAt,
                signatureOk = signatureOk,
                source = "seed",
                entries = entries,
                installedAt = IsoTime.format(nowMillis),
            ),
        )
        val settings = settingsDao.getSync() ?: UserSettings()
        settingsDao.upsert(settings.copy(datasetVersion = version))
    }
}
