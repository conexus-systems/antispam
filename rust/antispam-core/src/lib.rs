//! AntiSpam BR - Core anti-spam engine in Rust
//!
//! This crate provides the core functionality for:
//! - Phone number normalization (Brazilian E.164)
//! - Community reputation computation
//! - Spam decision engine
//! - Dataset format (ASBR binary format)
//! - Ed25519 signature verification

pub mod crypto;
pub mod dataset;
pub mod decision;
pub mod error;
pub mod phone;
pub mod reputation;

pub use crate::error::{AntispamError, Result};

/// Library version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Initialize the library (no-op, for future use)
pub fn init() {}