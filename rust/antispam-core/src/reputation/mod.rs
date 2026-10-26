use crate::error::{AntispamError, Result};
use indexmap::IndexMap;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

/// Category matching TypeScript implementation
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Category {
    Other,
    Telemarketing,
    Robocall,
    SilentCall,
    Collection,
    BankScam,
    PixScam,
    Phishing,
    DeliveryScam,
    FakeSupport,
    Loan,
    Survey,
    Spoofing,
    Legitimate,
}

impl Category {
    pub fn as_str(&self) -> &'static str {
        match self {
            Category::Other => "OTHER",
            Category::Telemarketing => "TELEMARKETING",
            Category::Robocall => "ROBOCALL",
            Category::SilentCall => "SILENT_CALL",
            Category::Collection => "COLLECTION",
            Category::BankScam => "BANK_SCAM",
            Category::PixScam => "PIX_SCAM",
            Category::Phishing => "PHISHING",
            Category::DeliveryScam => "DELIVERY_SCAM",
            Category::FakeSupport => "FAKE_SUPPORT",
            Category::Loan => "LOAN",
            Category::Survey => "SURVEY",
            Category::Spoofing => "SPOOFING",
            Category::Legitimate => "LEGITIMATE",
        }
    }

    pub fn severity(&self) -> f64 {
        match self {
            Category::Other => 0.7,
            Category::Telemarketing => 0.8,
            Category::Robocall => 0.85,
            Category::SilentCall => 0.8,
            Category::Collection => 0.75,
            Category::BankScam => 1.0,
            Category::PixScam => 1.0,
            Category::Phishing => 1.0,
            Category::DeliveryScam => 1.0,
            Category::FakeSupport => 1.0,
            Category::Loan => 0.85,
            Category::Survey => 0.6,
            Category::Spoofing => 0.95,
            Category::Legitimate => 0.0,
        }
    }
}

impl std::str::FromStr for Category {
    type Err = AntispamError;
    fn from_str(s: &str) -> Result<Self> {
        match s.to_uppercase().as_str() {
            "OTHER" => Ok(Category::Other),
            "TELEMARKETING" => Ok(Category::Telemarketing),
            "ROBOCALL" => Ok(Category::Robocall),
            "SILENT_CALL" => Ok(Category::SilentCall),
            "COLLECTION" => Ok(Category::Collection),
            "BANK_SCAM" => Ok(Category::BankScam),
            "PIX_SCAM" => Ok(Category::PixScam),
            "PHISHING" => Ok(Category::Phishing),
            "DELIVERY_SCAM" => Ok(Category::DeliveryScam),
            "FAKE_SUPPORT" => Ok(Category::FakeSupport),
            "LOAN" => Ok(Category::Loan),
            "SURVEY" => Ok(Category::Survey),
            "SPOOFING" => Ok(Category::Spoofing),
            "LEGITIMATE" => Ok(Category::Legitimate),
            _ => Err(AntispamError::Internal(format!("Unknown category: {}", s))),
        }
    }
}

/// Reputation label
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Label {
    #[default]
    Clean,
    LowRisk,
    Suspicious,
    Spam,
    HighRisk,
}

/// Reputation configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReputationConfig {
    pub half_life_days: f64,
    pub min_weighted_reporters: f64,
    pub saturation: f64,
    pub contest_factor: f64,
    pub burst_window_hours: f64,
    pub burst_share: f64,
    pub young_reporter_days: f64,
    pub network_cap: f64,
    pub contest_anchor_days: f64,
    pub labels: Vec<LabelThreshold>,
    pub publication: PublicationConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LabelThreshold {
    pub label: Label,
    pub min: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PublicationConfig {
    pub min_weighted_reporters: f64,
    pub min_age_hours: f64,
    pub min_score: f64,
    pub min_mature_share: f64,
    pub min_networks: usize,
}

impl Default for ReputationConfig {
    fn default() -> Self {
        Self {
            half_life_days: 30.0,
            min_weighted_reporters: 2.0,
            saturation: 3.0,
            contest_factor: 1.0,
            burst_window_hours: 1.0,
            burst_share: 0.7,
            young_reporter_days: 7.0,
            network_cap: 1.0,
            contest_anchor_days: 30.0,
            labels: vec![
                LabelThreshold { label: Label::Clean, min: 0.0 },
                LabelThreshold { label: Label::LowRisk, min: 20.0 },
                LabelThreshold { label: Label::Suspicious, min: 40.0 },
                LabelThreshold { label: Label::Spam, min: 60.0 },
                LabelThreshold { label: Label::HighRisk, min: 80.0 },
            ],
            publication: PublicationConfig {
                min_weighted_reporters: 3.0,
                min_age_hours: 48.0,
                min_score: 60.0,
                min_mature_share: 0.5,
                min_networks: 3,
            },
        }
    }
}

/// Reporter statistics for weight calculation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReporterStats {
    pub age_days: f64,
    pub agreements: u64,
    pub disagreements: u64,
    pub quarantined: bool,
    pub outlier_today: Option<bool>,
}

/// Input report
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ReportInput {
    pub reporter: String,
    pub category: Category,
    pub at: i64, // epoch ms
    pub weight: f64,
    pub reporter_age_days: f64,
    pub network: Option<String>,
}

/// Number context
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct NumberContext {
    pub never_block: Option<bool>,
    pub verified_org: Option<bool>,
    pub pending_contest: Option<bool>,
    pub contest_accepted: Option<bool>,
}

/// Factor in reputation breakdown
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Factor {
    pub factor: String,
    pub value: f64,
    pub note: String,
}

/// Reputation result
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Reputation {
    pub score: u8,
    pub label: Label,
    pub category: Option<Category>,
    pub weighted_reporters: f64,
    pub contest_weight: f64,
    pub distinct_reporters: usize,
    pub insufficient_evidence: bool,
    pub burst_quarantine: bool,
    pub disputed: bool,
    pub verified_org: bool,
    pub first_report_at: Option<i64>,
    pub last_report_at: Option<i64>,
    pub publishable: bool,
    pub blocked_by: Vec<String>,
    pub factors: Vec<Factor>,
}

const HOUR_MS: f64 = 3_600_000.0;
const DAY_MS: f64 = 24.0 * HOUR_MS;

fn round3(x: f64) -> f64 {
    (x * 1000.0).round() / 1000.0
}

fn label_for(score: f64, config: &ReputationConfig) -> Label {
    let mut label = Label::Clean;
    for l in &config.labels {
        if score >= l.min {
            label = l.label;
        }
    }
    label
}

/// Cap weight by network
fn cap_by_network(reports: &[ReportInput], cap: f64) -> Vec<ReportInput> {
    let mut totals: HashMap<String, f64> = HashMap::new();
    for r in reports {
        let key = r.network.clone().unwrap_or_else(|| format!("r:{}", r.reporter));
        *totals.entry(key).or_default() += r.weight;
    }
    reports
        .iter()
        .map(|r| {
            let key = r.network.clone().unwrap_or_else(|| format!("r:{}", r.reporter));
            let total = totals.get(&key).copied().unwrap_or(0.0);
            if total > cap {
                let mut r = r.clone();
                r.weight = r.weight * (cap / total);
                r
            } else {
                r.clone()
            }
        })
        .collect()
}

/// Burst weight detection
fn burst_weight(reports: &[ReportInput], config: &ReputationConfig) -> f64 {
    let mut young: Vec<ReportInput> = reports
        .iter()
        .filter(|r| r.reporter_age_days < config.young_reporter_days)
        .cloned()
        .collect();
    young.sort_by_key(|r| r.at);

    let window = config.burst_window_hours * HOUR_MS;
    let mut best = 0.0;
    let mut sum = 0.0;
    let mut start = 0;
    for end in 0..young.len() {
        sum += young[end].weight;
        while young[end].at - young[start].at > window as i64 {
            sum -= young[start].weight;
            start += 1;
        }
        best = f64::max(best, sum);
    }
    best
}

/// Main reputation computation
pub fn compute_reputation(
    input: &[ReportInput],
    now: i64,
    ctx: &NumberContext,
    config: &ReputationConfig,
) -> Reputation {
    // Latest report per reporter
    let mut latest: HashMap<String, ReportInput> = HashMap::new();
    for r in input {
        if r.at > now {
            continue;
        }
        let prev = latest.get(&r.reporter);
        if prev.is_none() || r.at > prev.unwrap().at {
            latest.insert(r.reporter.clone(), r.clone());
        }
    }
    let reports: Vec<ReportInput> = latest.into_values().collect();

    // Split abuse vs legitimate
    let abuse: Vec<ReportInput> = reports.iter().filter(|r| r.category != Category::Legitimate).cloned().collect();
    let legit: Vec<ReportInput> = reports.iter().filter(|r| r.category == Category::Legitimate).cloned().collect();

    let abuse_capped = cap_by_network(&abuse, config.network_cap);
    let legit_capped = cap_by_network(&legit, config.network_cap);

    let decay = |r: &ReportInput| -> f64 {
        0.5_f64.powf((now as f64 - r.at as f64) / DAY_MS / config.half_life_days)
    };

    let spam_weight: f64 = abuse_capped.iter().map(|r| r.weight * decay(r)).sum();
    let mature_contest: f64 = legit_capped
        .iter()
        .filter(|r| r.reporter_age_days >= config.contest_anchor_days)
        .map(|r| r.weight * decay(r))
        .sum();
    let young_contest: f64 = legit_capped
        .iter()
        .filter(|r| r.reporter_age_days < config.contest_anchor_days)
        .map(|r| r.weight * decay(r))
        .sum();
    let contest_weight = mature_contest + young_contest.min(mature_contest);

    // Category with highest weight
    let mut by_category: HashMap<Category, f64> = HashMap::new();
    for r in &abuse_capped {
        *by_category.entry(r.category).or_default() += r.weight * decay(r);
    }
    let (category, best) = by_category
        .iter()
        .max_by(|a, b| a.1.partial_cmp(b.1).unwrap())
        .map(|(c, w)| (*c, *w))
        .unwrap_or((Category::Other, 0.0));

    let severity = if spam_weight > 0.0 {
        by_category.iter().map(|(c, w)| c.severity() * w).sum::<f64>() / spam_weight
    } else {
        0.0
    };

    let net = spam_weight - config.contest_factor * contest_weight;
    let insufficient_evidence = spam_weight < config.min_weighted_reporters;

    let mut score = 0;
    if !ctx.contest_accepted.unwrap_or(false) && !insufficient_evidence && net > 0.0 {
        score = (100.0 * (1.0 - (-net / config.saturation).exp()) * severity).round() as u8;
    }
    score = score.min(100).max(0);

    let raw_abuse: f64 = abuse_capped.iter().map(|r| r.weight).sum();
    let burst_quarantine = raw_abuse > 0.0
        && burst_weight(&abuse_capped, config) / raw_abuse > config.burst_share
        && raw_abuse >= config.min_weighted_reporters;

    let ats: Vec<i64> = abuse_capped.iter().map(|r| r.at).collect();
    let first_report_at = ats.iter().min().copied();
    let last_report_at = ats.iter().max().copied();
    let disputed = ctx.pending_contest.unwrap_or(false) || contest_weight >= 1.0;

    let mature_weight: f64 = abuse_capped
        .iter()
        .filter(|r| r.reporter_age_days >= config.young_reporter_days)
        .map(|r| r.weight * decay(r))
        .sum();

    let networks: HashSet<String> = abuse_capped
        .iter()
        .filter(|r| r.weight > 0.0)
        .map(|r| r.network.clone().unwrap_or_else(|| format!("r:{}", r.reporter)))
        .collect();

    let pub_cfg = &config.publication;
    let mut blocked_by = Vec::new();
    if ctx.contest_accepted.unwrap_or(false) {
        blocked_by.push("CONTEST_ACCEPTED".to_string());
    }
    if ctx.never_block.unwrap_or(false) {
        blocked_by.push("NEVER_BLOCK".to_string());
    }
    if spam_weight < pub_cfg.min_weighted_reporters {
        blocked_by.push("FEW_REPORTERS".to_string());
    }
    if first_report_at.is_none() || ((now - first_report_at.unwrap()) as f64) < pub_cfg.min_age_hours * HOUR_MS {
        blocked_by.push("TOO_RECENT".to_string());
    }
    if (score as f64) < pub_cfg.min_score {
        blocked_by.push("LOW_SCORE".to_string());
    }
    if spam_weight > 0.0 && mature_weight / spam_weight < pub_cfg.min_mature_share {
        blocked_by.push("YOUNG_REPORTERS".to_string());
    }
    if networks.len() < pub_cfg.min_networks {
        blocked_by.push("FEW_NETWORKS".to_string());
    }
    if burst_quarantine {
        blocked_by.push("BURST_QUARANTINE".to_string());
    }
    if ctx.pending_contest.unwrap_or(false) {
        blocked_by.push("PENDING_CONTEST".to_string());
    }

    let mut factors = vec![
        Factor {
            factor: "weighted_reporters".to_string(),
            value: round3(spam_weight),
            note: format!("{} denunciantes distintos, ponderados por confiança e recência", abuse_capped.len()),
        },
        Factor {
            factor: "contest_weight".to_string(),
            value: round3(contest_weight),
            note: format!("{} contestações \"é legítimo\"", legit_capped.len()),
        },
        Factor {
            factor: "severity".to_string(),
            value: round3(severity),
            note: if category != Category::Other {
                format!("gravidade média; predominante {}", category.as_str())
            } else {
                "sem denúncias".to_string()
            },
        },
        Factor {
            factor: "networks".to_string(),
            value: networks.len() as f64,
            note: "redes distintas entre os denunciantes (peso por rede limitado)".to_string(),
        },
        Factor {
            factor: "half_life_days".to_string(),
            value: config.half_life_days,
            note: "denúncias perdem metade do peso a cada meia-vida".to_string(),
        },
    ];

    if insufficient_evidence {
        factors.push(Factor {
            factor: "insufficient_evidence".to_string(),
            value: 1.0,
            note: "poucos denunciantes confiáveis: número neutro".to_string(),
        });
    }
    if burst_quarantine {
        factors.push(Factor {
            factor: "burst_quarantine".to_string(),
            value: 1.0,
            note: "surto de denúncias de dispositivos novos: aguarda moderação".to_string(),
        });
    }

    Reputation {
        score,
        label: label_for(score as f64, config),
        category: if category == Category::Other && best == 0.0 { None } else { Some(category) },
        weighted_reporters: round3(spam_weight),
        contest_weight: round3(contest_weight),
        distinct_reporters: abuse_capped.len(),
        insufficient_evidence,
        burst_quarantine,
        disputed,
        verified_org: ctx.verified_org.unwrap_or(false),
        first_report_at,
        last_report_at,
        publishable: blocked_by.is_empty(),
        blocked_by,
        factors,
    }
}

/// Calculate reporter weight
pub fn reporter_weight(stats: &ReporterStats) -> f64 {
    if stats.quarantined || stats.outlier_today.unwrap_or(false) {
        return 0.0;
    }
    let maturity = (stats.age_days.max(0.0) / 60.0).min(1.0);
    let accuracy = (stats.agreements as f64 + 1.0) / (stats.agreements as f64 + stats.disagreements as f64 + 2.0);
    let base = 0.3 + 0.4 * maturity + 0.3 * (2.0 * accuracy - 1.0).max(0.0);
    (base * (2.0 * accuracy).min(1.0)).min(1.0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_reporter_weight() {
        let stats = ReporterStats {
            age_days: 60.0,
            agreements: 10,
            disagreements: 0,
            quarantined: false,
            outlier_today: Some(false),
        };
        let w = reporter_weight(&stats);
        assert!(w > 0.9);

        let new_reporter = ReporterStats {
            age_days: 0.0,
            agreements: 0,
            disagreements: 0,
            quarantined: false,
            outlier_today: Some(false),
        };
        let w = reporter_weight(&new_reporter);
        assert!((w - 0.3).abs() < 0.01);
    }

    #[test]
    fn test_compute_reputation_empty() {
        let rep = compute_reputation(&[], 0, &NumberContext::default(), &ReputationConfig::default());
        assert_eq!(rep.score, 0);
        assert_eq!(rep.label, Label::Clean);
        assert!(rep.insufficient_evidence);
    }

    #[test]
    fn test_compute_reputation_basic() {
        let reports = vec![
            ReportInput {
                reporter: "user1".to_string(),
                category: Category::Telemarketing,
                at: 0,
                weight: 1.0,
                reporter_age_days: 30.0,
                network: None,
            },
            ReportInput {
                reporter: "user2".to_string(),
                category: Category::Telemarketing,
                at: 0,
                weight: 1.0,
                reporter_age_days: 30.0,
                network: None,
            },
        ];
        let now = 1 * 24 * 3600 * 1000; // 1 day later (minimal decay)
        let mut config = ReputationConfig::default();
        config.min_weighted_reporters = 1.5; // Allow 2 reporters with slight decay
        let rep = compute_reputation(&reports, now, &NumberContext::default(), &config);
        assert!(rep.score > 0);
        assert!(!rep.insufficient_evidence);
    }
}