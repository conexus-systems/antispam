package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Uma decisão de triagem. Em BLOCK a chamada some do registro do sistema (skipCallLog),
 * então esta tabela é a única evidência: toda decisão é gravada. Spec RF-HIST-01/02.
 */
@Serializable
@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["at"], orders = [Index.Order.DESC], name = "index_history_entries_at"),
        Index(value = ["number"], name = "index_history_entries_number"),
    ],
)
data class HistoryEntry(
    @PrimaryKey
    val id: String,
    val number: String,                 // E.164 canônico (ou texto original se não normalizável)
    val display: String,                // Formatado para exibição
    val name: String? = null,           // Nome do contato (resolvido localmente)
    val at: String,                     // ISO 8601 UTC, formato fixo com milissegundos (ordenável)
    val action: String,                 // ALLOW, WARN, SILENCE, BLOCK
    val score: Int,
    val confidence: String = "LOW",     // LOW, MEDIUM, HIGH
    val reasons: String,                // JSON array de DecisionReason (nomes)
    val explanation: String = "[]",     // JSON array de textos PT-BR
    val category: String? = null,       // Categoria de denúncia se houver
    val reported: Boolean = false,
    val simulated: Boolean = false,
)
