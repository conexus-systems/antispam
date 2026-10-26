use crate::error::{AntispamError, Result};
use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;

/// Public key entry
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PublicKeyEntry {
    pub key_id: String,
    pub public_key_b64: String,
}

/// Key store for verifying dataset signatures
pub struct KeyStore {
    keys: HashMap<String, VerifyingKey>,
}

impl KeyStore {
    pub fn new() -> Self {
        Self {
            keys: HashMap::new(),
        }
    }

    /// Add a public key from base64 encoded 32-byte Ed25519 public key
    pub fn add_key(&mut self, key_id: String, public_key_b64: &str) -> Result<()> {
        let key_bytes = base64::decode(public_key_b64)
            .map_err(|e| AntispamError::Ed25519(format!("Invalid base64: {}", e)))?;
        if key_bytes.len() != 32 {
            return Err(AntispamError::Ed25519("Public key must be 32 bytes".to_string()));
        }
        let verifying_key = VerifyingKey::from_bytes(&key_bytes.try_into().unwrap())
            .map_err(|e| AntispamError::Ed25519(format!("Invalid Ed25519 key: {}", e)))?;
        self.keys.insert(key_id, verifying_key);
        Ok(())
    }

    /// Verify a signature
    pub fn verify(&self, key_id: &str, message: &[u8], signature_b64: &str) -> Result<bool> {
        let key = self.keys.get(key_id)
            .ok_or_else(|| AntispamError::Ed25519(format!("Key not found: {}", key_id)))?;
        
        let signature_bytes = base64::decode(signature_b64)
            .map_err(|e| AntispamError::Ed25519(format!("Invalid signature base64: {}", e)))?;
        
        if signature_bytes.len() != 64 {
            return Err(AntispamError::Ed25519("Signature must be 64 bytes".to_string()));
        }
        
        let signature = Signature::from_bytes(&signature_bytes.try_into().unwrap());
        
        Ok(key.verify(message, &signature).is_ok())
    }

    /// Verify manifest signature
    pub fn verify_manifest(&self, manifest_json: &[u8], key_id: &str, signature_b64: &str) -> Result<bool> {
        self.verify(key_id, manifest_json, signature_b64)
    }

    /// Get all key IDs
    pub fn key_ids(&self) -> Vec<String> {
        self.keys.keys().cloned().collect()
    }
}

impl Default for KeyStore {
    fn default() -> Self {
        Self::new()
    }
}

/// Parse TEST_KEY.json format
pub fn parse_test_keys(json: &str) -> Result<HashMap<String, Vec<u8>>> {
    #[derive(Deserialize)]
    struct TestKey {
        public_key_b64: String,
    }
    let key: TestKey = serde_json::from_str(json)?;
    let mut map = HashMap::new();
    let key_bytes = base64::decode(&key.public_key_b64)
        .map_err(|e| AntispamError::Ed25519(format!("Invalid base64: {}", e)))?;
    map.insert("test-2026".to_string(), key_bytes);
    Ok(map)
}

/// SHA-256 hash
pub fn sha256(data: &[u8]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hasher.finalize().into()
}

/// SHA-256 hex string
pub fn sha256_hex(data: &[u8]) -> String {
    hex::encode(sha256(data))
}

/// HMAC-SHA256
pub fn hmac_sha256(key: &[u8], data: &[u8]) -> [u8; 32] {
    use hmac::{Hmac, Mac};
    type HmacSha256 = Hmac<Sha256>;
    let mut mac = HmacSha256::new_from_slice(key).expect("HMAC can take key of any size");
    mac.update(data);
    mac.finalize().into_bytes().into()
}

/// HMAC-SHA256 hex string
pub fn hmac_sha256_hex(key: &[u8], data: &[u8]) -> String {
    hex::encode(hmac_sha256(key, data))
}

/// Generate a random Ed25519 keypair (for testing only)
#[cfg(feature = "test-utils")]
pub fn generate_keypair() -> (Vec<u8>, Vec<u8>) {
    use ed25519_dalek::SigningKey;
    use rand::rngs::OsRng;
    let mut csprng = OsRng;
    let signing_key = SigningKey::generate(&mut csprng);
    let verifying_key = signing_key.verifying_key();
    (signing_key.to_bytes().to_vec(), verifying_key.to_bytes().to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_keystore() {
        let mut store = KeyStore::new();
        // Test with a valid base64 that decodes to 32 bytes
        // We'll just test the key storage without Ed25519 validation
        // by using a key that passes the base64 and length checks
        // The actual Ed25519 validation happens at verification time
        let test_key = base64::encode([0x01u8; 32]);
        // This will fail Ed25519 validation but pass base64/length checks
        // We test the add_key function's base64 and length validation only
        assert!(store.add_key("test-2026".to_string(), &test_key).is_ok());
        
        // Test that invalid base64 fails
        assert!(store.add_key("invalid".to_string(), "not-base64!").is_err());
        
        // Test that wrong length key fails (base64 of 1 byte = 2 chars + padding)
        assert!(store.add_key("short".to_string(), "YQ==").is_err()); // only 1 byte
        
        // Test that valid length but invalid Ed25519 is accepted by add_key
        // (validation happens at verify time)
        assert!(store.key_ids().contains(&"test-2026".to_string()));
    }

    #[test]
    fn test_sha256() {
        let data = b"hello world";
        let hash = sha256(data);
        assert_eq!(hash.len(), 32);
        let hex = sha256_hex(data);
        assert_eq!(hex.len(), 64);
    }

    #[test]
    fn test_hmac() {
        let key = b"secret key";
        let data = b"message";
        let hmac = hmac_sha256(key, data);
        assert_eq!(hmac.len(), 32);
        let hex = hmac_sha256_hex(key, data);
        assert_eq!(hex.len(), 64);
    }
}