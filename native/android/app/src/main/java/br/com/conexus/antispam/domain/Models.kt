package br.com.conexus.antispam.domain

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.DecisionReason
import br.com.conexus.antispam.decision.StirShaken

/** Modelos de UI: as telas só enxergam estes tipos (nunca entidades Room). */

/** Item do histórico, já decodificado. */
data class HistoryItem(
    val id: String,
    /** E.164, ou o texto original quando não normalizável (ex.: oculto = ""). */
    val number: String,
    /** Formatado para exibir, ex.: "+55 (11) 91234-5678" ou "Número oculto". */
    val display: String,
    val name: String?,
    val atMillis: Long,
    val action: CallAction,
    val score: Int,
    val confidence: Confidence,
    val reasons: List<DecisionReason>,
    /** Motivos legíveis em PT-BR (RF-HIST-02). */
    val explanation: List<String>,
    /** Categoria da denúncia, se houver. */
    val category: String?,
    val reported: Boolean,
    val simulated: Boolean,
)

enum class HistoryFilter { ALL, BLOCKED, SILENCED, WARNED, ALLOWED }

/** Totais por ação (chamadas reais). */
data class HistoryCounts(val blocked: Int, val silenced: Int, val warned: Int, val allowed: Int) {
    val total: Int get() = blocked + silenced + warned + allowed
}

/** Linha da lista bloqueada/permitida. */
data class ListItem(
    val number: String,
    val display: String,
    val note: String?,
    /** "local", "report", "remote" ou "rule". */
    val source: String,
    val createdAtMillis: Long,
)

enum class ListKind { BLOCKED, ALLOWED }

/** Regra para a tela de Regras. */
data class RuleItem(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val kind: RuleKind,
    val value: String,
    val action: CallAction,
    val priority: Int,
    /** Regras de sistema (0303, ocultos, internacionais) não podem ser apagadas. */
    val isSystem: Boolean,
)

enum class RuleKind { PREFIX, DDD, HIDDEN, INTERNATIONAL, NOT_CONTACT, SCHEDULE, REGEX }

/** Categoria escolhida ao denunciar (RF-DENUN-01). */
enum class ReportCategory(val code: String, val label: String) {
    TELEMARKETING("telemarketing", "Telemarketing"),
    SCAM("fraud", "Golpe"),
    COLLECTION("collection", "Cobrança"),
    OTHER("spam", "Outro"),
}

/** Linha "Base local: versão X, de <data>". */
data class DatasetInfo(
    val version: Long,
    val publishedAt: String,
    val installedAtMillis: Long,
    val entries: Int,
    val source: String,
    val signatureOk: Boolean,
)

/** Entrada do pipeline: o que se sabe sobre a chamada. Verificar/Home só preenchem [rawNumber]. */
data class ScreeningRequest(
    val rawNumber: String,
    val presentation: CallPresentation = CallPresentation.UNKNOWN,
    val stirShaken: StirShaken = StirShaken.UNKNOWN,
    val contactName: String? = null,
    /** null = agora. */
    val atMillis: Long? = null,
)

/** Estado do número nas listas, para decidir quais botões mostrar. */
enum class ListStatus { NONE, BLOCKED, ALLOWED }

/** Política de resposta lida das configurações no mesmo instante da decisão. */
data class ResponsePolicy(val rejectBlockedCalls: Boolean = true, val notify: Boolean = true)

/** Resultado único para serviço, Verificar e simulador da Home. */
data class ScreeningResult(
    val request: ScreeningRequest,
    val decision: CallDecision,
    /** E.164 canônico ou texto original. */
    val number: String,
    val displayNumber: String,
    /** Rótulo curto da ação: "Bloquear", "Silenciar", "Alertar", "Permitir". */
    val actionLabel: String,
    /** Frase de destaque para o resultado, ex.: "Provável spam". */
    val headline: String,
    /** Faixa de risco: "Mínimo", "Baixo", "Médio", "Alto", "Crítico". */
    val riskLabel: String,
    /** Motivos legíveis em PT-BR. */
    val explanation: List<String>,
    val listStatus: ListStatus,
    val policy: ResponsePolicy,
    val atMillis: Long,
)
