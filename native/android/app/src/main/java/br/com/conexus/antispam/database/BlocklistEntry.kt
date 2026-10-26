package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "blocklist_entries")
data class BlocklistEntry(
    @PrimaryKey
    val number: String,                 // Número canônico
    val action: String,                 // "block" ou "allow"
    val source: String = "local",       // local, remote, rule
    val createdAt: String = ""          // ISO 8601
)