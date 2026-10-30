package br.com.conexus.antispam.domain

import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.Confidence
import br.com.conexus.antispam.decision.DecisionReason

/** Rótulos PT-BR para a UI. */

fun CallAction.label(): String = when (this) {
    CallAction.BLOCK -> "Bloquear"
    CallAction.SILENCE -> "Silenciar"
    CallAction.WARN -> "Alertar"
    CallAction.ALLOW -> "Permitir"
}

/** Particípio, para linhas do histórico. */
fun CallAction.pastLabel(): String = when (this) {
    CallAction.BLOCK -> "Bloqueada"
    CallAction.SILENCE -> "Silenciada"
    CallAction.WARN -> "Alerta"
    CallAction.ALLOW -> "Permitida"
}

fun Confidence.label(): String = when (this) {
    Confidence.HIGH -> "Alta"
    Confidence.MEDIUM -> "Média"
    Confidence.LOW -> "Baixa"
}

fun DecisionReason.label(): String = when (this) {
    DecisionReason.MODE_UNKNOWN -> "Proteção desativada"
    DecisionReason.EMERGENCY_NUMBER -> "Número de emergência"
    DecisionReason.CONTACT -> "Contato"
    DecisionReason.ALLOWLIST -> "Lista de permitidos"
    DecisionReason.BLACKLIST -> "Lista de bloqueados"
    DecisionReason.USER_RULE -> "Regra sua"
    DecisionReason.BRAZIL_RULE -> "Rota de telemarketing (0303)"
    DecisionReason.LOCAL_REPUTATION -> "Reputação local"
    DecisionReason.CAMPAIGN -> "Campanha detectada"
    DecisionReason.STIR_SHAKEN -> "Verificação STIR/SHAKEN"
    DecisionReason.PATTERN -> "Padrão suspeito"
    DecisionReason.FAILSAFE -> "Permitida por segurança"
}

fun headlineFor(action: CallAction, reasons: List<DecisionReason>): String = when {
    DecisionReason.EMERGENCY_NUMBER in reasons -> "Número de emergência"
    DecisionReason.MODE_UNKNOWN in reasons -> "Proteção desativada"
    DecisionReason.FAILSAFE in reasons -> "Não foi possível analisar"
    action == CallAction.BLOCK -> "Provável spam"
    action == CallAction.SILENCE -> "Suspeita: chamada silenciada"
    action == CallAction.WARN -> "Atenção: sinais de risco"
    else -> "Nenhum sinal de risco"
}

fun riskBandLabel(score: Int): String = when {
    score >= 90 -> "Crítico"
    score >= 70 -> "Alto"
    score >= 50 -> "Médio"
    score >= 30 -> "Baixo"
    else -> "Mínimo"
}
