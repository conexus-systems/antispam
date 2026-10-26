package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey
    val id: Int = 1,                    // Singleton
    val mode: String = "BALANCED",      // OFF, BASIC, BALANCED, AGGRESSIVE, CUSTOM
    val blockThreshold: Int = 75,
    val silenceThreshold: Int = 85,
    val warnThreshold: Int = 55,
    val handle0303: String = "silence", // allow, identify, silence, block
    val blockUnknownInAggressive: Boolean = false,
    val theme: String = "system",       // system, dark, light
    val accent: String = "#1BA1E2",
    val reduceMotion: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val datasetVersion: Long = 0
)