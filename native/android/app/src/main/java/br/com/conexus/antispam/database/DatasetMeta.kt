package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Metadados da base local de reputação (linha única, id = 1). Spec §6: "Base local: versão X". */
@Entity(tableName = "dataset_meta")
data class DatasetMeta(
    @PrimaryKey
    val id: Int = 1,
    val version: Long = 0,
    val publishedAt: String = "",
    val signatureOk: Boolean = false,
    val source: String = "seed",        // seed, delta
    val entries: Int = 0,
    val installedAt: String = "",
)
