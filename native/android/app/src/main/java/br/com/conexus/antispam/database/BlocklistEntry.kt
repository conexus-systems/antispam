package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Lista bloqueada e permitida na mesma tabela: a PK única garante a exclusividade. */
@Serializable
@Entity(tableName = "blocklist_entries")
data class BlocklistEntry(
    @PrimaryKey
    val number: String,                 // Número canônico E.164
    val action: String,                 // "block" ou "allow"
    val source: String = "local",       // local, report, remote, rule
    val createdAt: String = "",         // ISO 8601
    val note: String? = null,
)
