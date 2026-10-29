package br.com.conexus.antispam.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Regra do usuário/sistema. Mapeia para decision.Rule. priority menor = vale primeiro. Spec §6. */
@Entity(tableName = "rules", indices = [Index(value = ["priority"], name = "index_rules_priority")])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val kind: String,                   // PREFIX, DDD, HIDDEN, INTERNATIONAL, NOT_CONTACT, SCHEDULE, REGEX
    val value: String = "",
    val action: String,                 // BLOCK, SILENCE, WARN, ALLOW
    val priority: Int,
    val createdAt: String = "",
)

/** Regras de sistema semeadas na criação do banco. Não podem ser apagadas, só ligadas/desligadas ou ter a ação trocada. */
object SystemRules {
    const val ZERO_THREE_ZERO_ID = 1L
    const val HIDDEN_ID = 2L
    const val INTERNATIONAL_ID = 3L

    /** Regras de usuário usam prioridade < SYSTEM_PRIORITY_BASE e, portanto, valem antes das de sistema. */
    const val SYSTEM_PRIORITY_BASE = 1000

    fun isSystem(id: Long): Boolean = id == ZERO_THREE_ZERO_ID || id == HIDDEN_ID || id == INTERNATIONAL_ID
}
