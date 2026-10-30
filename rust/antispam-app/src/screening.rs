//! Ponte entre a UI e o núcleo `antispam-core`: monta o contexto de uma verificação manual
//! e converte a decisão em texto pt-BR para a tela.

use antispam_core::decision::{
    CallAction, CallContext, CallPresentation, DecisionEngine, DecisionEngineConfig, EngineInput,
    StirShaken,
};
use std::collections::HashSet;

/// Modos exibidos em Ajustes, na mesma ordem do seletor.
pub const MODE_LABELS: [&str; 4] = ["Desligado", "Básico", "Equilibrado", "Agressivo"];

pub fn config_for(index: i32) -> DecisionEngineConfig {
    match index {
        0 => DecisionEngineConfig::off(),
        1 => DecisionEngineConfig::basic(),
        3 => DecisionEngineConfig::aggressive(),
        _ => DecisionEngineConfig::balanced(),
    }
}

/// Resultado de uma verificação manual, pronto para a UI.
#[derive(Debug, Clone)]
pub struct Verdict {
    pub number: String,
    pub action: &'static str,
    pub explanation: String,
}

/// Verifica um número digitado pelo usuário, sem contatos nem listas (a agenda entra depois).
pub fn verify(raw: &str, mode_index: i32, at: i64) -> Verdict {
    let engine = DecisionEngine::new();
    let input = EngineInput {
        config: config_for(mode_index),
        ctx: CallContext {
            raw_number: raw.to_string(),
            contact_name: None,
            presentation: CallPresentation::Unknown,
            stir_shaken: StirShaken::NotVerified,
            at,
        },
        contact_numbers: HashSet::new(),
        allowlist: HashSet::new(),
        blacklist: HashSet::new(),
        rules: Vec::new(),
        recent_calls: Vec::new(),
        previously_blocked: HashSet::new(),
        previously_allowed: HashSet::new(),
        reputation_reports: Vec::new(),
    };
    let decision = engine.decide(input);
    Verdict {
        number: raw.trim().to_string(),
        action: action_label(decision.action),
        explanation: decision.explanation.join(" "),
    }
}

fn action_label(action: CallAction) -> &'static str {
    match action {
        CallAction::Allow => "Permitida",
        CallAction::Warn => "Atenção",
        CallAction::Silence => "Silenciada",
        CallAction::Block => "Bloqueada",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn emergencia_nunca_bloqueada() {
        let v = verify("190", 3, 0);
        assert_eq!(v.action, "Permitida");
    }

    #[test]
    fn desligado_sempre_permite() {
        let v = verify("0800 123 4567", 0, 0);
        assert_eq!(v.action, "Permitida");
    }

    #[test]
    fn numero_comum_tem_explicacao() {
        let v = verify("11 99999-9999", 2, 0);
        assert!(!v.explanation.is_empty());
    }
}
