package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.RuleEntity
import br.com.conexus.antispam.database.SystemRules
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.Rule
import br.com.conexus.antispam.decision.RuleMatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class RuleResult {
    data class Saved(val rule: RuleItem) : RuleResult()
    /** [message] já em PT-BR, pronta para exibir. */
    data class Invalid(val message: String) : RuleResult()
}

/**
 * Regras (RF-REG-01/02). A ordem é a prioridade: a primeira regra ativa que casa vale (como no motor).
 *
 * Formatos de [RuleItem.value] por [RuleKind]:
 * - PREFIX: E.164 parcial com '+', ex.: "+551140" (aceita "11 4000" -> "+55114000" sem espaços)
 * - DDD: dois dígitos, ex.: "21"
 * - REGEX: expressão aplicada ao E.164 (containsMatchIn)
 * - SCHEDULE: "dias|início|fim", ex.: "0,1,2,3,4|08:00|18:00" ("*" = todos os dias; 0 = segunda)
 * - HIDDEN, INTERNATIONAL, NOT_CONTACT: sem valor
 */
@Singleton
class RulesRepository @Inject constructor(
    private val dao: RuleDao,
    private val clock: Clock,
) {

    /** Em ordem de prioridade (a primeira vale primeiro). */
    fun observe(): Flow<List<RuleItem>> = dao.observeAll().map { list -> list.map(::toItem) }

    suspend fun list(): List<RuleItem> = dao.getAll().map(::toItem)

    /** Regras no tipo do motor, em ordem de prioridade. */
    suspend fun toEngineRules(): List<Rule> = dao.getAll().mapNotNull { toEngineRule(it) }

    suspend fun create(
        name: String,
        kind: RuleKind,
        value: String,
        action: CallAction,
        enabled: Boolean = true,
    ): RuleResult {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return RuleResult.Invalid("Dê um nome à regra")
        val normalized = normalizeValue(kind, value) ?: return RuleResult.Invalid(invalidMessage(kind))
        val max = dao.getAll().filter { it.priority < SystemRules.SYSTEM_PRIORITY_BASE }.maxOfOrNull { it.priority } ?: 0
        val entity = RuleEntity(
            name = cleanName,
            enabled = enabled,
            kind = kind.name,
            value = normalized,
            action = action.name,
            priority = max + 10,
            createdAt = IsoTime.format(clock.nowMillis()),
        )
        val id = dao.upsert(entity)
        return RuleResult.Saved(toItem(entity.copy(id = id)))
    }

    /** Edita nome/valor/ação de uma regra (o tipo não muda). */
    suspend fun update(id: Long, name: String, value: String, action: CallAction): RuleResult {
        val current = dao.get(id) ?: return RuleResult.Invalid("Regra não encontrada")
        val kind = kindOf(current.kind) ?: return RuleResult.Invalid("Regra inválida")
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return RuleResult.Invalid("Dê um nome à regra")
        val normalized = normalizeValue(kind, value) ?: return RuleResult.Invalid(invalidMessage(kind))
        val updated = current.copy(name = cleanName, value = normalized, action = action.name)
        dao.upsert(updated)
        return RuleResult.Saved(toItem(updated))
    }

    /** Liga/desliga sem apagar. */
    suspend fun setEnabled(id: Long, enabled: Boolean) { dao.setEnabled(id, enabled) }

    /** Apaga. Regras de sistema não são apagadas (devolve false). */
    suspend fun delete(id: Long): Boolean {
        if (SystemRules.isSystem(id)) return false
        return dao.delete(id) > 0
    }

    /**
     * Reordena as regras de USUÁRIO: [orderedIds] na nova ordem (primeira = maior prioridade).
     * Regras de sistema continuam depois das de usuário.
     */
    suspend fun reorder(orderedIds: List<Long>) {
        var p = 10
        for (id in orderedIds) {
            if (SystemRules.isSystem(id)) continue
            dao.setPriority(id, p)
            p += 10
        }
    }

    /** Valor normalizado para o [kind], ou null se inválido. */
    fun normalizeValue(kind: RuleKind, raw: String): String? {
        val v = raw.trim()
        return when (kind) {
            RuleKind.HIDDEN, RuleKind.INTERNATIONAL, RuleKind.NOT_CONTACT -> ""
            RuleKind.DDD -> v.filter { it.isDigit() }.takeIf { it.length == 2 && it[0] != '0' }
            RuleKind.PREFIX -> {
                val digits = v.filter { it.isDigit() }
                when {
                    v.startsWith("+") -> digits.takeIf { it.length >= 3 }?.let { "+$it" }
                    else -> {
                        // "0303" (discagem nacional) -> "+55303"; "11 4000" -> "+55114000"
                        val d = digits.trimStart('0')
                        when {
                            d.length < 3 -> null
                            d.startsWith("55") && d.length >= 4 -> "+$d"
                            else -> "+55$d"
                        }
                    }
                }
            }
            RuleKind.REGEX -> v.takeIf { it.isNotEmpty() && runCatching { Regex(it) }.isSuccess }
            RuleKind.SCHEDULE -> parseSchedule(v)?.let { s ->
                "${s.weekdays?.joinToString(",") ?: "*"}|${s.start}|${s.end}"
            }
        }
    }

    private fun invalidMessage(kind: RuleKind) = when (kind) {
        RuleKind.DDD -> "DDD inválido (use 2 dígitos)"
        RuleKind.PREFIX -> "Prefixo inválido (mínimo 3 dígitos)"
        RuleKind.REGEX -> "Expressão regular inválida"
        RuleKind.SCHEDULE -> "Horário inválido (ex.: 0,1,2|08:00|18:00)"
        else -> "Valor inválido"
    }

    private fun toItem(e: RuleEntity) = RuleItem(
        id = e.id,
        name = e.name,
        enabled = e.enabled,
        kind = kindOf(e.kind) ?: RuleKind.PREFIX,
        value = e.value,
        action = actionOf(e.action),
        priority = e.priority,
        isSystem = SystemRules.isSystem(e.id),
    )

    companion object {
        private fun kindOf(s: String): RuleKind? = runCatching { RuleKind.valueOf(s) }.getOrNull()
        private fun actionOf(s: String): CallAction = runCatching { CallAction.valueOf(s) }.getOrDefault(CallAction.ALLOW)

        private val HHMM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        private fun parseSchedule(v: String): RuleMatch.BySchedule? {
            val parts = v.split("|")
            if (parts.size != 3) return null
            val (days, start, end) = parts
            if (!HHMM.matches(start) || !HHMM.matches(end)) return null
            val weekdays = if (days.trim() == "*") null else {
                val list = days.split(",").map { it.trim().toIntOrNull() ?: return null }
                if (list.isEmpty() || list.any { it !in 0..6 }) return null
                list
            }
            return RuleMatch.BySchedule(weekdays, start, end)
        }

        /** Entidade -> Rule do motor. Regra malformada vira null (ignorada; nunca derruba a chamada). */
        fun toEngineRule(e: RuleEntity): Rule? {
            val match: RuleMatch = when (kindOf(e.kind) ?: return null) {
                RuleKind.PREFIX -> RuleMatch.ByPrefix(e.value)
                RuleKind.DDD -> RuleMatch.ByDdd(e.value)
                RuleKind.HIDDEN -> RuleMatch.Hidden
                RuleKind.INTERNATIONAL -> RuleMatch.International
                RuleKind.NOT_CONTACT -> RuleMatch.NotContact
                RuleKind.REGEX -> RuleMatch.ByRegex(e.value)
                RuleKind.SCHEDULE -> parseSchedule(e.value) ?: return null
            }
            return Rule(name = e.name, enabled = e.enabled, match = match, action = actionOf(e.action))
        }
    }
}
