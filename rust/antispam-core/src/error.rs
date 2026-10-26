use thiserror::Error;

#[derive(Error, Debug)]
pub enum AntispamError {
    #[error("Invalid phone number: {0}")]
    InvalidPhone(String),

    #[error("Invalid E.164 format: {0}")]
    InvalidE164(String),

    #[error("Invalid DDD: {0}")]
    InvalidDdd(String),

    #[error("Normalization failed: {0}")]
    NormalizationFailed(String),

    #[error("Dataset error: {0}")]
    DatasetError(String),

    #[error("Manifest validation failed: {0}")]
    ManifestValidation(String),

    #[error("Signature verification failed: {0}")]
    SignatureVerification(String),

    #[error("Ed25519 error: {0}")]
    Ed25519(String),

    #[error("Checksum mismatch: expected {expected}, got {actual}")]
    ChecksumMismatch { expected: String, actual: String },

    #[error("Version rollback detected: installed {installed}, incoming {incoming}")]
    VersionRollback { installed: u64, incoming: u64 },

    #[error("Dataset expired at {0}")]
    DatasetExpired(String),

    #[error("Unsupported compression: {0}")]
    UnsupportedCompression(String),

    #[error("Invalid binary format: {0}")]
    InvalidBinaryFormat(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),

    #[error("UTF-8 error: {0}")]
    Utf8(#[from] std::string::FromUtf8Error),

    #[error("Hex decode error: {0}")]
    Hex(#[from] hex::FromHexError),

    #[error("Base64 decode error: {0}")]
    Base64(#[from] base64::DecodeError),

    #[error("Internal error: {0}")]
    Internal(String),
}

pub type Result<T> = std::result::Result<T, AntispamError>;