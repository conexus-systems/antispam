use crate::error::{AntispamError, Result};
use crate::phone::{normalize, NormalizedNumber, NumberKind};
use crate::reputation::{compute_reputation, ReputationConfig, ReportInput, NumberContext};
use chrono::{DateTime, Datelike, Local, Timelike};
use indexmap::IndexMap;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

/// Protection mode
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ProtectionMode {
    Off,
    Basic,
    Balanced,
    Aggressive,
}

/// Call action
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CallAction {
    Allow,
    Warn,
    Silence,
    Block,
}

/// Confidence level
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Confidence {
    Low,
    Medium,
    High,
}

/// Decision reason
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DecisionReason {
    ModeUnknown,
    EmergencyNumber,
    Contact,
    Allowlist,
    Blacklist,
    UserRule,
    BrazilRule,
    LocalReputation,
    Campaign,
    StirShaken,
    Pattern,
    Failsafe,
}

/// STIR/SHAKEN status
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum StirShaken {
    Pass,
    Fail,
    NotVerified,
}

/// Call presentation
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CallPresentation {
    Allowed,
    Restricted,
    Unknown,
}

/// Engine configuration per mode
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DecisionEngineConfig {
    pub mode: ProtectionMode,
    pub block_threshold: u8,
    pub silence_threshold: u8,
    pub warn_threshold: u8,
    pub handle_0303: String, // "allow", "identify", "silence", "block"
    pub block_unknown_in_aggressive: bool,
}

impl Default for DecisionEngineConfig {
    fn default() -> Self {
        Self::balanced()
    }
}

impl DecisionEngineConfig {
    pub fn off() -> Self {
        Self {
            mode: ProtectionMode::Off,
            block_threshold: 101,
            silence_threshold: 101,
            warn_threshold: 101,
            handle_0303: "allow".to_string(),
            block_unknown_in_aggressive: false,
        }
    }

    pub fn basic() -> Self {
        Self {
            mode: ProtectionMode::Basic,
            block_threshold: 90,
            silence_threshold: 95,
            warn_threshold: 70,
            handle_0303: "identify".to_string(),
            block_unknown_in_aggressive: false,
        }
    }

    pub fn balanced() -> Self {
        Self {
            mode: ProtectionMode::Balanced,
            block_threshold: 75,
            silence_threshold: 85,
            warn_threshold: 55,
            handle_0303: "silence".to_string(),
            block_unknown_in_aggressive: false,
        }
    }

    pub fn aggressive() -> Self {
        Self {
            mode: ProtectionMode::Aggressive,
            block_threshold: 60,
            silence_threshold: 70,
            warn_threshold: 40,
            handle_0303: "block".to_string(),
            block_unknown_in_aggressive: true,
        }
    }
}

/// Rule match type
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "value")]
pub enum RuleMatch {
    Number(String),
    Prefix(String),
    Ddd(String),
    Regex { pattern: String },
    Hidden,
    International,
    NotContact,
    Schedule {
        weekdays: Option<Vec<u8>>,
        start: String, // HH:MM
        end: String,
    },
}

/// User rule
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub name: String,
    pub enabled: bool,
    #[serde(rename = "match")]
    pub match_: RuleMatch,
    pub action: CallAction,
}

/// Rule evaluation context
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleContext {
    pub is_contact: bool,
    pub at: i64,
    pub presentation: CallPresentation,
}

/// Rule evaluation result
#[derive(Debug, Clone)]
pub struct RuleEval {
    pub matched: Option<Rule>,
    pub action: Option<CallAction>,
}

/// Campaign signal
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CampaignSignal {
    pub number: String,
    pub at: i64,
}

/// Campaign detection result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CampaignDetection {
    pub risk: u8,
    pub similar_count: usize,
}

/// Engine input
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EngineInput {
    pub config: DecisionEngineConfig,
    pub ctx: CallContext,
    pub contact_numbers: HashSet<String>,
    pub allowlist: HashSet<String>,
    pub blacklist: HashSet<String>,
    pub rules: Vec<Rule>,
    pub recent_calls: Vec<CampaignSignal>,
    pub previously_blocked: HashSet<String>,
    pub previously_allowed: HashSet<String>,
    pub reputation_reports: Vec<ReportInput>,
}

/// Call context
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallContext {
    pub raw_number: String,
    pub contact_name: Option<String>,
    pub presentation: CallPresentation,
    pub stir_shaken: StirShaken,
    pub at: i64,
}

/// Score contribution
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScoreContribution {
    pub label: String,
    pub points: i8,
}

/// Spam score breakdown
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SpamScoreBreakdown {
    pub score: u8,
    pub confidence: Confidence,
    pub contributions: Vec<ScoreContribution>,
}

/// Call decision result
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallDecision {
    pub action: CallAction,
    pub score: u8,
    pub confidence: Confidence,
    pub reasons: Vec<DecisionReason>,
    pub explanation: Vec<String>,
    pub normalized: NormalizedNumber,
}

/// Main decision engine
pub struct DecisionEngine;

impl DecisionEngine {
    pub fn new() -> Self {
        Self
    }

    /// Main decision function with fail-safe
    pub fn decide(&self, input: EngineInput) -> CallDecision {
        // Clone raw_number for fail-safe path before moving input
        let raw_number = input.ctx.raw_number.clone();
        match self.decide_unsafe(input) {
            Ok(decision) => decision,
            Err(e) => {
                // FAIL-SAFE: internal error -> ALLOW
                let normalized = normalize(Some(&raw_number), None);
                CallDecision {
                    action: CallAction::Allow,
                    score: 0,
                    confidence: Confidence::Low,
                    reasons: vec![DecisionReason::Failsafe],
                    explanation: vec![format!("Falha interna ao analisar a chamada — permitida por segurança (fail-safe): {}", e)],
                    normalized,
                }
            }
        }
    }

    fn decide_unsafe(&self, input: EngineInput) -> Result<CallDecision> {
        let config = &input.config;
        let ctx = &input.ctx;
        let normalized = normalize(Some(&ctx.raw_number), None);
        let mut explanation = Vec::new();
        let mut reasons = Vec::new();

        // 0) Mode OFF
        if config.mode == ProtectionMode::Off {
            return Ok(self.allow_decision(
                normalized,
                vec![DecisionReason::ModeUnknown],
                vec!["Proteção desativada — chamada permitida.".to_string()],
            ));
        }

        // 1) Emergency - ABSOLUTE RULE
        if normalized.kind == NumberKind::Emergency {
            return Ok(self.allow_decision(
                normalized,
                vec![DecisionReason::EmergencyNumber],
                vec!["Número de emergência — nunca é bloqueado.".to_string()],
            ));
        }

        // 2) Contacts / whitelist
        let is_contact = ctx.contact_name.as_ref().map(|n| !n.is_empty()).unwrap_or(false)
            || input.contact_numbers.contains(&normalized.e164.clone().unwrap_or_default());
        if is_contact {
            return Ok(self.allow_decision(
                normalized,
                vec![DecisionReason::Contact],
                vec![format!("Chamada de contato ({}).", ctx.contact_name.as_ref().unwrap_or(&"na agenda".to_string()))],
            ));
        }
        if let Some(e164) = &normalized.e164 {
            if input.allowlist.contains(e164) {
                return Ok(self.allow_decision(
                    normalized,
                    vec![DecisionReason::Allowlist],
                    vec!["Número na sua lista de permitidos.".to_string()],
                ));
            }
        }

        // 3) Explicit blacklist
        if let Some(e164) = &normalized.e164 {
            if input.blacklist.contains(e164) {
                return Ok(CallDecision {
                    action: CallAction::Block,
                    score: 100,
                    confidence: Confidence::High,
                    reasons: vec![DecisionReason::Blacklist],
                    explanation: vec!["Número na sua lista de bloqueados.".to_string()],
                    normalized,
                });
            }
        }

        // 4) User rules (first match wins)
        let rule_ctx = RuleContext {
            is_contact,
            at: ctx.at,
            presentation: ctx.presentation,
        };
        let rule_eval = self.evaluate_rules(&input.rules, &normalized, &rule_ctx);
        if let (Some(matched), Some(action)) = (rule_eval.matched, rule_eval.action) {
            explanation.push(format!("Regra \"{}\" aplicada.", matched.name));
            if action == CallAction::Allow {
                return Ok(self.allow_decision(normalized, vec![DecisionReason::UserRule], explanation));
            }
            return Ok(CallDecision {
                action,
                score: if action == CallAction::Block { 95 } else { 70 },
                confidence: Confidence::High,
                reasons: vec![DecisionReason::UserRule],
                explanation,
                normalized,
            });
        }

        // 5) 0303 specific handling
        if self.is_0303(&normalized) && !["allow", "identify"].contains(&config.handle_0303.as_str()) {
            let action = if config.handle_0303 == "block" {
                CallAction::Block
            } else {
                CallAction::Silence
            };
            return Ok(CallDecision {
                action,
                score: 88,
                confidence: Confidence::Medium,
                reasons: vec![DecisionReason::BrazilRule],
                explanation: vec!["Chamada 0303 (rota de telemarketing) — tratamento configurado por você.".to_string()],
                normalized,
            });
        }

        // 6) Composite score
        let reputation = if let Some(e164) = &normalized.e164 {
            compute_reputation(
                &input.reputation_reports,
                ctx.at,
                &NumberContext::default(),
                &ReputationConfig::default(),
            )
        } else {
            Default::default()
        };
        let rep_signal = if reputation.insufficient_evidence {
            None
        } else {
            Some((reputation.score, reputation.weighted_reporters, reputation.label))
        };

        let campaign = self.detect_campaign(
            CampaignSignal {
                number: normalized.e164.clone().unwrap_or_default(),
                at: ctx.at,
            },
            &input.recent_calls,
            ctx.at,
        );

        let previously_blocked = normalized.e164.as_ref().map(|e| input.previously_blocked.contains(e)).unwrap_or(false);
        let previously_allowed = normalized.e164.as_ref().map(|e| input.previously_allowed.contains(e)).unwrap_or(false);

        let campaign_clone = campaign.clone();
        let breakdown = self.compute_spam_score(
            &normalized,
            rep_signal,
            if campaign.risk > 0 { Some(campaign) } else { None },
            ctx.stir_shaken,
            previously_blocked,
            previously_allowed,
        );

        explanation.extend(breakdown.contributions.iter().map(|c| format!("{} (+{})", c.label, c.points)));

        // 7) Aggressive mode: international outside contacts
        let (final_score, final_confidence) = if config.block_unknown_in_aggressive
            && !is_contact
            && normalized.kind == NumberKind::International
        {
            let new_score = breakdown.score.max(config.block_threshold);
            let new_confidence = if new_score >= config.block_threshold {
                Confidence::Medium
            } else {
                breakdown.confidence
            };
            (new_score, new_confidence)
        } else {
            (breakdown.score, breakdown.confidence)
        };

        // 8) Final decision
        let action = self.action_for_score(final_score, config, final_confidence);

        if action == CallAction::Allow && final_score >= config.warn_threshold / 2 && !breakdown.contributions.is_empty() {
            reasons.push(DecisionReason::Pattern);
        }
        if campaign_clone.risk > 0 {
            reasons.push(DecisionReason::Campaign);
        }
        if rep_signal.is_some() {
            reasons.push(DecisionReason::LocalReputation);
        }
        if ctx.stir_shaken == StirShaken::Fail {
            reasons.push(DecisionReason::StirShaken);
        }
        if action != CallAction::Allow && reasons.is_empty() {
            reasons.push(DecisionReason::Pattern);
        }

        let final_explanation = if action == CallAction::Allow && final_score == 0 {
            vec!["Nenhum sinal de risco encontrado.".to_string()]
        } else {
            let mut exp = vec![format!("Score {}/100 — {}.", final_score, self.risk_band(final_score))];
            exp.extend(explanation);
            exp
        };

        Ok(CallDecision {
            action,
            score: final_score,
            confidence: final_confidence,
            reasons,
            explanation: final_explanation,
            normalized,
        })
    }

    fn allow_decision(&self, n: NormalizedNumber, reasons: Vec<DecisionReason>, explanation: Vec<String>) -> CallDecision {
        CallDecision {
            action: CallAction::Allow,
            score: 0,
            confidence: Confidence::High,
            reasons,
            explanation,
            normalized: n,
        }
    }

    fn action_for_score(&self, score: u8, config: &DecisionEngineConfig, confidence: Confidence) -> CallAction {
        if score >= config.block_threshold {
            if confidence == Confidence::Low && score < config.block_threshold + 15 {
                CallAction::Silence
            } else {
                CallAction::Block
            }
        } else if score >= config.silence_threshold {
            CallAction::Silence
        } else if score >= config.warn_threshold {
            CallAction::Warn
        } else {
            CallAction::Allow
        }
    }

    fn is_0303(&self, n: &NormalizedNumber) -> bool {
        if let Some(e164) = &n.e164 {
            // Normalized numbers have the leading 0 stripped, so 0303 becomes 55303
            e164.starts_with("+55303") || e164.starts_with("0303")
        } else {
            false
        }
    }

    fn evaluate_rules(&self, rules: &[Rule], phone: &NormalizedNumber, ctx: &RuleContext) -> RuleEval {
        for rule in rules.iter().filter(|r| r.enabled) {
            if self.matches_rule(rule, phone, ctx) {
                return RuleEval {
                    matched: Some(rule.clone()),
                    action: Some(rule.action),
                };
            }
        }
        RuleEval { matched: None, action: None }
    }

    fn matches_rule(&self, rule: &Rule, phone: &NormalizedNumber, ctx: &RuleContext) -> bool {
        match &rule.match_ {
            RuleMatch::Number(value) => phone.e164.as_ref() == Some(value),
            RuleMatch::Prefix(value) => phone.e164.as_ref().map(|e| e.starts_with(value)).unwrap_or(false),
            RuleMatch::Ddd(value) => phone.ddd.as_ref() == Some(value),
            RuleMatch::Regex { pattern } => {
                if let Some(e164) = &phone.e164 {
                    regex::Regex::new(pattern).ok().map(|re| re.is_match(e164)).unwrap_or(false)
                } else {
                    false
                }
            }
            RuleMatch::Hidden => phone.kind == NumberKind::Hidden || ctx.presentation == CallPresentation::Restricted,
            RuleMatch::International => phone.kind == NumberKind::International,
            RuleMatch::NotContact => !ctx.is_contact,
            RuleMatch::Schedule { weekdays, start, end } => {
                use chrono::{Local, Timelike};
                let now = Local::now();
                let weekday = now.weekday().num_days_from_monday() as u8; // 0=Mon...6=Sun
                let time_str = format!("{:02}:{:02}", now.hour(), now.minute());
                let in_weekdays = weekdays.as_ref().map(|w| w.contains(&weekday)).unwrap_or(true);
                let in_time = time_str >= *start && time_str <= *end;
                in_weekdays && in_time
            }
        }
    }

    fn detect_campaign(&self, current: CampaignSignal, recent_calls: &[CampaignSignal], now: i64) -> CampaignDetection {
        let window_ms = 30 * 60 * 1000; // 30 min
        let similar: Vec<_> = recent_calls
            .iter()
            .filter(|call| {
                let diff = now - call.at;
                diff >= 0 && diff <= window_ms && self.is_similar_prefix(&current.number, &call.number)
            })
            .collect();
        let count = similar.len();
        let risk = match count {
            c if c >= 5 => 30,
            c if c >= 3 => 15,
            c if c >= 2 => 8,
            _ => 0,
        };
        CampaignDetection { risk, similar_count: count }
    }

    fn is_similar_prefix(&self, a: &str, b: &str) -> bool {
        let min_len = a.len().min(b.len()).min(8);
        a[..min_len] == b[..min_len]
    }

    fn compute_spam_score(
        &self,
        phone: &NormalizedNumber,
        reputation: Option<(u8, f64, crate::reputation::Label)>,
        campaign: Option<CampaignDetection>,
        stir_shaken: StirShaken,
        previously_blocked: bool,
        previously_allowed: bool,
    ) -> SpamScoreBreakdown {
        let mut contributions = Vec::new();
        let mut score: i32 = 0;

        // Local reputation
        if let Some((rep_score, rep_weight, _)) = reputation {
            let points = (rep_score as f64 * rep_weight * 0.6) as i32;
            score = score.saturating_add(points);
            contributions.push(ScoreContribution {
                label: "Reputação local".to_string(),
                points: points as i8,
            });
        }

        // Campaign
        if let Some(camp) = campaign {
            if camp.risk > 0 {
                score = score.saturating_add(camp.risk as i32);
                contributions.push(ScoreContribution {
                    label: format!("Campanha detectada ({} chamadas similares)", camp.similar_count),
                    points: camp.risk as i8,
                });
            }
        }

        // STIR/SHAKEN
        match stir_shaken {
            StirShaken::Fail => {
                score = score.saturating_add(25);
                contributions.push(ScoreContribution {
                    label: "STIR/SHAKEN falhou".to_string(),
                    points: 25,
                });
            }
            StirShaken::Pass => {
                score = score.saturating_sub(10);
                contributions.push(ScoreContribution {
                    label: "STIR/SHAKEN válido".to_string(),
                    points: -10,
                });
            }
            StirShaken::NotVerified => {}
        }

        // User history
        if previously_blocked {
            score = score.saturating_add(30);
            contributions.push(ScoreContribution {
                label: "Bloqueado anteriormente por você".to_string(),
                points: 30,
            });
        }
        if previously_allowed {
            score = score.saturating_sub(20);
            contributions.push(ScoreContribution {
                label: "Permitido anteriormente por você".to_string(),
                points: -20,
            });
        }

        // Phone patterns
        score = score.saturating_add(self.score_phone_patterns(phone, &mut contributions));

        // Confidence
        let confidence = if contributions.iter().any(|c| c.points >= 30) {
            Confidence::High
        } else if contributions.iter().any(|c| c.points >= 15) {
            Confidence::Medium
        } else {
            Confidence::Low
        };

        SpamScoreBreakdown {
            score: score.clamp(0, 100) as u8,
            confidence,
            contributions,
        }
    }

    fn score_phone_patterns(&self, phone: &NormalizedNumber, contributions: &mut Vec<ScoreContribution>) -> i32 {
        let mut points = 0;
        let digits = phone.e164.as_ref().map(|e| e.trim_start_matches('+')).unwrap_or("");

        // Repeated sequences (9999, 12345)
        if self.has_repeated_sequence(digits) || self.has_sequential_digits(digits) {
            points += 5;
            contributions.push(ScoreContribution {
                label: "Padrão numérico suspeito".to_string(),
                points: 5,
            });
        }

        // Short code non-emergency
        if phone.kind == NumberKind::ShortCode && phone.kind != NumberKind::Emergency {
            points += 10;
            contributions.push(ScoreContribution {
                label: "Código curto não emergencial".to_string(),
                points: 10,
            });
        }

        points
    }

    fn has_repeated_sequence(&self, digits: &str) -> bool {
        for i in 0..digits.len().saturating_sub(3) {
            let sub = &digits[i..i+4];
            if sub.chars().all(|c| c == sub.chars().next().unwrap()) {
                return true;
            }
        }
        false
    }

    fn has_sequential_digits(&self, digits: &str) -> bool {
        let sequences = ["0123456789", "9876543210"];
        for seq in sequences {
            for i in 0..seq.len().saturating_sub(3) {
                if digits.contains(&seq[i..i+4]) {
                    return true;
                }
            }
        }
        false
    }

    fn risk_band(&self, score: u8) -> &'static str {
        match score {
            s if s >= 90 => "Crítico",
            s if s >= 70 => "Alto",
            s if s >= 50 => "Médio",
            s if s >= 30 => "Baixo",
            _ => "Mínimo",
        }
    }
}

impl Default for DecisionEngine {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    fn make_input(raw: &str) -> EngineInput {
        EngineInput {
            config: DecisionEngineConfig::balanced(),
            ctx: CallContext {
                raw_number: raw.to_string(),
                contact_name: None,
                presentation: CallPresentation::Allowed,
                stir_shaken: StirShaken::NotVerified,
                at: chrono::Local::now().timestamp_millis(),
            },
            contact_numbers: HashSet::new(),
            allowlist: HashSet::new(),
            blacklist: HashSet::new(),
            rules: Vec::new(),
            recent_calls: Vec::new(),
            previously_blocked: HashSet::new(),
            previously_allowed: HashSet::new(),
            reputation_reports: Vec::new(),
        }
    }

    #[test]
    fn test_emergency() {
        let engine = DecisionEngine::new();
        let decision = engine.decide(make_input("190"));
        assert_eq!(decision.action, CallAction::Allow);
        assert!(decision.reasons.contains(&DecisionReason::EmergencyNumber));
    }

    #[test]
    fn test_allowlist() {
        let mut input = make_input("+5511987654321");
        input.allowlist.insert("+5511987654321".to_string());
        let engine = DecisionEngine::new();
        let decision = engine.decide(input);
        assert_eq!(decision.action, CallAction::Allow);
        assert!(decision.reasons.contains(&DecisionReason::Allowlist));
    }

    #[test]
    fn test_blacklist() {
        let mut input = make_input("+5511987654321");
        input.blacklist.insert("+5511987654321".to_string());
        let engine = DecisionEngine::new();
        let decision = engine.decide(input);
        assert_eq!(decision.action, CallAction::Block);
        assert_eq!(decision.score, 100);
    }

    #[test]
    fn test_0303_balanced() {
        let engine = DecisionEngine::new();
        let input = make_input("03031234567");
        // Debug: print normalized number
        let normalized = crate::phone::normalize(Some("03031234567"), None);
        eprintln!("DEBUG: normalized = {:?}", normalized);
        let decision = engine.decide(input);
        assert_eq!(decision.action, CallAction::Silence);
        assert!(decision.reasons.contains(&DecisionReason::BrazilRule));
    }

    #[test]
    fn test_0303_aggressive_block() {
        let mut input = make_input("03031234567");
        input.config = DecisionEngineConfig::aggressive();
        let engine = DecisionEngine::new();
        let decision = engine.decide(input);
        assert_eq!(decision.action, CallAction::Block);
    }
}