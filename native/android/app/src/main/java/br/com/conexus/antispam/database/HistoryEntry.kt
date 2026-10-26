package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey
    val id: String,
    val number: String,                 // Número canônico
    val display: String,                // Formatado para exibição
    val name: String? = null,           // Nome do contato (resolvido localmente)
    val at: String,                     // ISO 8601
    val action: String,                 // ALLOW, WARN, SILENCE, BLOCK
    val score: Int,
    val reasons: String,                // JSON array de DecisionReason
    val category: String? = null,       // Categoria de denúncia se houver
    val simulated: Boolean = false
)