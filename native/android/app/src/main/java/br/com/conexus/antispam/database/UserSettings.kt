package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Configurações do usuário (linha única, id = 1). Spec §6. */
@Serializable
@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey
    val id: Int = 1,                    // Singleton
    val mode: String = "BALANCED",      // OFF, BASIC, BALANCED, AGGRESSIVE, CUSTOM
    val blockThreshold: Int = 75,
    val silenceThreshold: Int = 85,
    val warnThreshold: Int = 55,
    /** allow, identify, silence, block. Espelhado na regra de sistema "0303" (ver SystemRules). */
    val handle0303: String = "silence",
    val blockUnknownInAggressive: Boolean = false,
    val theme: String = "system",       // system, dark, light
    val accent: String = "#1BA1E2",
    val reduceMotion: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val onboardingDone: Boolean = false,
    val datasetVersion: Long = 0,
    /** Espelhado na regra de sistema "ocultos" (enabled). */
    val blockHidden: Boolean = false,
    /** Espelhado na regra de sistema "internacionais" (enabled). */
    val blockInternational: Boolean = false,
    val historyRetentionDays: Int = 90,
    val consentShareReports: Boolean = false,
    /** BLOCK: true = setRejectCall (quem liga ouve ocupado); false = só setDisallowCall. */
    val rejectBlockedCalls: Boolean = true,
)
