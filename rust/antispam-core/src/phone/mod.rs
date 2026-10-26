use crate::error::{AntispamError, Result};
use indexmap::IndexSet;
use lazy_static::lazy_static;
use std::collections::HashSet;

/// Number kind classification matching the TypeScript implementation
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum NumberKind {
    Mobile,
    Landline,
    TollFree,
    Telemarketing,
    SharedCost,
    Donation,
    Premium,
    NonGeoUnique,
    Emergency,
    ShortCode,
    International,
    LocalNoDdd,
    Hidden,
    Invalid,
}

impl NumberKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            NumberKind::Mobile => "MOBILE",
            NumberKind::Landline => "LANDLINE",
            NumberKind::TollFree => "TOLL_FREE",
            NumberKind::Telemarketing => "TELEMARKETING",
            NumberKind::SharedCost => "SHARED_COST",
            NumberKind::Donation => "DONATION",
            NumberKind::Premium => "PREMIUM",
            NumberKind::NonGeoUnique => "NON_GEO_UNIQUE",
            NumberKind::Emergency => "EMERGENCY",
            NumberKind::ShortCode => "SHORT_CODE",
            NumberKind::International => "INTERNATIONAL",
            NumberKind::LocalNoDdd => "LOCAL_NO_DDD",
            NumberKind::Hidden => "HIDDEN",
            NumberKind::Invalid => "INVALID",
        }
    }
}

/// Normalized number result
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct NormalizedNumber {
    pub input: String,
    pub kind: NumberKind,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub e164: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub ddd: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub shard: Option<String>,
}

// Valid Brazilian DDDs (matching TypeScript VALID_DDDS)
const VALID_DDDS: &[&str] = &[
    "11", "12", "13", "14", "15", "16", "17", "18", "19",
    "21", "22", "24", "27", "28",
    "31", "32", "33", "34", "35", "37", "38",
    "41", "42", "43", "44", "45", "46", "47", "48", "49",
    "51", "53", "54", "55",
    "61", "62", "63", "64", "65", "66", "67", "68", "69",
    "71", "73", "74", "75", "77", "79",
    "81", "82", "83", "84", "85", "86", "87", "88", "89",
    "91", "92", "93", "94", "95", "96", "97", "98", "99",
];

lazy_static! {
    static ref VALID_DDD_SET: HashSet<&'static str> = VALID_DDDS.iter().copied().collect();
    static ref HIDDEN_TOKENS: HashSet<&'static str> = [
        "", "anonymous", "private", "unknown", "restricted", "privado", "desconhecido"
    ].iter().copied().collect();
    static ref NON_GEO_PREFIXES: Vec<(&'static str, NumberKind)> = vec![
        ("0800", NumberKind::TollFree),
        ("0303", NumberKind::Telemarketing),
        ("0300", NumberKind::SharedCost),
        ("0500", NumberKind::Donation),
        ("0900", NumberKind::Premium),
    ];
    static ref NON_GEO_UNIQUE_REGEX: regex::Regex =
        regex::Regex::new(r"^(300[0-9]|400[0-9]|4020|4062|4090|4091)\d{4}$").unwrap();
}

/// Emergency numbers (1XX, 112, 911)
fn is_emergency_or_utility(digits: &str) -> bool {
    if digits == "911" {
        return true;
    }
    if digits.len() == 3 && digits.starts_with('1') {
        return true;
    }
    (digits.len() == 4 || digits.len() == 5) && digits.starts_with("10")
}

/// Emergency in any form (with +55, 00, etc.)
fn is_emergency_in_any_form(digits: &str) -> bool {
    if digits.len() > 8 {
        return false;
    }
    let without_country = if digits.starts_with("55") && digits.len() > 3 {
        &digits[2..]
    } else {
        digits
    };
    let trim_zeros = |s: &str| -> String { s.trim_start_matches('0').to_string() };
    [digits.to_string(), trim_zeros(digits), trim_zeros(without_country)].iter().any(|d| {
        d.len() >= 3 && d.len() <= 5 && is_emergency_or_utility(d)
    })
}

/// Build NormalizedNumber with shard computation
fn build_result(
    input: String,
    kind: NumberKind,
    e164: Option<String>,
    ddd: Option<String>,
) -> NormalizedNumber {
    let shard = e164.as_ref().and_then(|e| {
        match kind {
            NumberKind::Mobile | NumberKind::Landline => ddd.as_ref().map(|d| format!("55-{}", d)),
            NumberKind::International => Some("55-intl".to_string()),
            _ => Some("55-ng".to_string()),
        }
    });
    NormalizedNumber { input, kind, e164, ddd, shard }
}

/// Normalize national number (10 or 11 digits after +55)
fn from_national(input: String, national: &str) -> NormalizedNumber {
    if national.len() != 10 && national.len() != 11 {
        return build_result(input, NumberKind::Invalid, None, None);
    }
    let ddd = &national[0..2];
    if !VALID_DDD_SET.contains(ddd) {
        return build_result(input, NumberKind::Invalid, None, None);
    }
    let sub = &national[2..];
    if sub.len() == 9 {
        if !sub.starts_with('9') {
            return build_result(input, NumberKind::Invalid, None, None);
        }
        return build_result(input, NumberKind::Mobile, Some(format!("+55{}{}", ddd, sub)), Some(ddd.to_string()));
    }
    let first = sub.chars().next().unwrap();
    if ('2'..='6').contains(&first) {
        return build_result(input, NumberKind::Landline, Some(format!("+55{}{}", ddd, sub)), Some(ddd.to_string()));
    }
    if ('7'..='9').contains(&first) {
        let mobile = format!("9{}", sub);
        return build_result(input, NumberKind::Mobile, Some(format!("+55{}{}", ddd, mobile)), Some(ddd.to_string()));
    }
    build_result(input, NumberKind::Invalid, None, None)
}

/// Non-geographic numbers (0800, 0303, etc.)
fn non_geo(input: String, digits: &str) -> Option<NormalizedNumber> {
    for (prefix, kind) in NON_GEO_PREFIXES.iter() {
        if digits.starts_with(prefix) && (digits.len() == 11 || digits.len() == 10) {
            // Remove leading 0 for E.164
            let e164 = format!("+55{}", &digits[1..]);
            return Some(build_result(input, *kind, Some(e164), None));
        }
    }
    None
}

/// Main normalization function matching TypeScript implementation
pub fn normalize(raw: Option<&str>, user_ddd: Option<&str>) -> NormalizedNumber {
    let input = raw.unwrap_or("").to_string();
    let trimmed = input.trim();

    // Hidden numbers
    if HIDDEN_TOKENS.contains(trimmed.to_lowercase().as_str()) {
        return build_result(input, NumberKind::Hidden, None, None);
    }

    let has_plus = trimmed.starts_with('+');
    let digits: String = trimmed.chars().filter(|c| c.is_ascii_digit()).collect();

    if digits.is_empty() {
        return build_result(input, NumberKind::Hidden, None, None);
    }

    // Emergency numbers
    if is_emergency_in_any_form(&digits) {
        return build_result(input, NumberKind::Emergency, None, None);
    }

    // International format with +
    if has_plus {
        if digits.starts_with("55") {
            let rest = &digits[2..];
            if let Some(ng) = non_geo(input.clone(), &format!("0{}", rest)) {
                return ng;
            }
            if NON_GEO_UNIQUE_REGEX.is_match(rest) {
                return build_result(input, NumberKind::NonGeoUnique, Some(format!("+55{}", rest)), None);
            }
            return from_national(input, rest);
        }
        if digits.len() >= 8 && digits.len() <= 15 {
            return build_result(input, NumberKind::International, Some(format!("+{}", digits)), None);
        }
        return build_result(input, NumberKind::Invalid, None, None);
    }

    // Short codes (no +)
    if digits.len() <= 5 {
        if is_emergency_or_utility(&digits) {
            return build_result(input, NumberKind::Emergency, None, None);
        }
        if digits.len() >= 4 {
            return build_result(input, NumberKind::ShortCode, None, None);
        }
        return build_result(input, NumberKind::Invalid, None, None);
    }

    // 00 prefix (international dialing)
    if digits.starts_with("00") {
        let intl = &digits[4..];
        if intl.starts_with("55") {
            return normalize(Some(&format!("+{}", intl)), user_ddd);
        }
        if intl.len() >= 8 && intl.len() <= 15 {
            return build_result(input, NumberKind::International, Some(format!("+{}", intl)), None);
        }
        return build_result(input, NumberKind::Invalid, None, None);
    }

    // Starts with 0 (non-geo or national with trunk)
    if digits.starts_with('0') {
        if let Some(ng) = non_geo(input.clone(), &digits) {
            return ng;
        }
        if digits.len() == 11 || digits.len() == 12 {
            return from_national(input, &digits[1..]);
        }
        if digits.len() == 13 || digits.len() == 14 {
            return from_national(input, &digits[3..]);
        }
        return build_result(input, NumberKind::Invalid, None, None);
    }

    // Non-geo unique without prefix
    if NON_GEO_UNIQUE_REGEX.is_match(&digits) {
        return build_result(input, NumberKind::NonGeoUnique, Some(format!("+55{}", digits)), None);
    }

    // 55 prefix without +
    if digits.starts_with("55") && (digits.len() == 12 || digits.len() == 13) {
        return from_national(input, &digits[2..]);
    }

    // National number (10 or 11 digits)
    if digits.len() == 10 || digits.len() == 11 {
        return from_national(input, &digits);
    }

    // Local number without DDD (8 or 9 digits)
    if digits.len() == 8 || digits.len() == 9 {
        if let Some(udd) = user_ddd {
            if VALID_DDD_SET.contains(udd) {
                return from_national(input, &format!("{}{}", udd, digits));
            }
        }
        return build_result(input, NumberKind::LocalNoDdd, None, None);
    }

    build_result(input, NumberKind::Invalid, None, None)
}

/// Convert E.164 to u64 key (digits without +)
pub fn e164_to_key(e164: &str) -> Result<u64> {
    if !regex::Regex::new(r"^\+\d{8,15}$").unwrap().is_match(e164) {
        return Err(AntispamError::InvalidE164(e164.to_string()));
    }
    Ok(e164[1..].parse::<u64>().map_err(|_| AntispamError::InvalidE164(e164.to_string()))?)
}

/// Get shard ID for a number
pub fn get_shard_id(e164: &str) -> Option<String> {
    if !e164.starts_with("+55") {
        return Some("55-intl".to_string());
    }
    let national = &e164[3..];
    if national.starts_with('0') {
        // Non-geographic
        Some("55-ng".to_string())
    } else if national.len() >= 2 {
        let ddd = &national[0..2];
        if VALID_DDD_SET.contains(ddd) {
            Some(format!("55-{}", ddd))
        } else {
            Some("55-ng".to_string())
        }
    } else {
        Some("55-ng".to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_emergency() {
        assert_eq!(normalize(Some("190"), None).kind, NumberKind::Emergency);
        assert_eq!(normalize(Some("192"), None).kind, NumberKind::Emergency);
        assert_eq!(normalize(Some("193"), None).kind, NumberKind::Emergency);
        assert_eq!(normalize(Some("+55190"), None).kind, NumberKind::Emergency);
        assert_eq!(normalize(Some("0190"), None).kind, NumberKind::Emergency);
        assert_eq!(normalize(Some("911"), None).kind, NumberKind::Emergency);
    }

    #[test]
    fn test_mobile() {
        let result = normalize(Some("+5511987654321"), None);
        assert_eq!(result.kind, NumberKind::Mobile);
        assert_eq!(result.e164, Some("+5511987654321".to_string()));
        assert_eq!(result.ddd, Some("11".to_string()));
        assert_eq!(result.shard, Some("55-11".to_string()));
    }

    #[test]
    fn test_landline() {
        let result = normalize(Some("+551132345678"), None);
        assert_eq!(result.kind, NumberKind::Landline);
        assert_eq!(result.e164, Some("+551132345678".to_string()));
    }

    #[test]
    fn test_toll_free() {
        let result = normalize(Some("08001234567"), None);
        assert_eq!(result.kind, NumberKind::TollFree);
        assert_eq!(result.e164, Some("+558001234567".to_string()));
    }

    #[test]
    fn test_telemarketing() {
        let result = normalize(Some("03031234567"), None);
        assert_eq!(result.kind, NumberKind::Telemarketing);
    }

    #[test]
    fn test_hidden() {
        assert_eq!(normalize(Some(""), None).kind, NumberKind::Hidden);
        assert_eq!(normalize(Some("private"), None).kind, NumberKind::Hidden);
        assert_eq!(normalize(Some("restricted"), None).kind, NumberKind::Hidden);
        assert_eq!(normalize(Some("privado"), None).kind, NumberKind::Hidden);
    }

    #[test]
    fn test_local_no_ddd() {
        let result = normalize(Some("987654321"), Some("11"));
        assert_eq!(result.kind, NumberKind::Mobile);
        assert_eq!(result.e164, Some("+5511987654321".to_string()));
    }

    #[test]
    fn test_local_no_ddd_fallback() {
        let result = normalize(Some("987654321"), None);
        assert_eq!(result.kind, NumberKind::LocalNoDdd);
    }

    #[test]
    fn test_international() {
        let result = normalize(Some("+14155551234"), None);
        assert_eq!(result.kind, NumberKind::International);
        assert_eq!(result.e164, Some("+14155551234".to_string()));
        assert_eq!(result.shard, Some("55-intl".to_string()));
    }

    #[test]
    fn test_short_code() {
        assert_eq!(normalize(Some("1234"), None).kind, NumberKind::ShortCode);
    }

    #[test]
    fn test_e164_to_key() {
        assert_eq!(e164_to_key("+5511987654321").unwrap(), 5511987654321);
        assert!(e164_to_key("5511987654321").is_err());
    }

    #[test]
    fn test_get_shard_id() {
        assert_eq!(get_shard_id("+5511987654321"), Some("55-11".to_string()));
        assert_eq!(get_shard_id("+558001234567"), Some("55-ng".to_string()));
        assert_eq!(get_shard_id("+14155551234"), Some("55-intl".to_string()));
    }
}