package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "reputation_entries")
data class ReputationEntry(
    @PrimaryKey
    val key: String,                    // Número canônico: +5511999999999
    val country: String = "BR",
    val category: String,               // spam, telemarketing, fraud, robocall, etc.
    val score: Int,                     // 0-100
    val reports: Int,                   // Total de denúncias
    val uniqueReporters: Int,           // Denunciantes únicos
    val firstSeen: String,              // ISO 8601
    val lastSeen: String,               // ISO 8601
    val confidence: Double = 0.5,       // 0.0-1.0
    val falsePositives: Int = 0,
    val reporterIds: String = ""        // JSON array de IDs anônimos locais
)