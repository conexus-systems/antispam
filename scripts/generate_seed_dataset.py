#!/usr/bin/env python3
"""
Script para gerar dataset seed assinado (Ed25519) para o APK.
Gera:
- android/app/src/main/assets/dataset_seed.json (manifest + entries)
- android/app/src/main/assets/dataset_public_key.der (chave pública PKCS#8)
- keys/ed25519_private.pem (chave privada - APENAS DEV, nunca no repo/CI produção)
"""

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ed25519
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat, PrivateFormat, NoEncryption

# ============================================================
# CONFIGURAÇÃO
# ============================================================

OUTPUT_DIR = Path("android/app/src/main/assets")
KEYS_DIR = Path("keys")
VERSION = 1
ENTRIES_COUNT = 50  # Seed mínimo para k-anonimato

# Chave privada DEV (gerada na primeira execução, salva em keys/)
# PRODUÇÃO: usar HSM / vault, NUNCA commitar chave privada
DEV_PRIVATE_KEY_PATH = KEYS_DIR / "ed25519_private.pem"
DEV_PUBLIC_KEY_PATH = KEYS_DIR / "ed25519_public.der"

# ============================================================
# ENTRADAS SEED (exemplos ilustrativos — SEM dados reais de terceiros)
# Números 55114002/4003 são faixas corporativas usadas como exemplo estrutural.
# A base real cresce por: denúncias locais + deltas comunitários assinados.
# ============================================================

SEED_ENTRIES = [
    {"key": "+551140028922", "country": "BR", "category": "telemarketing", "score": 78, "reports": 142, "uniqueReporters": 96, "firstSeen": "2024-01-15T10:00:00Z", "lastSeen": "2026-10-20T14:30:00Z", "confidence": 0.85, "falsePositives": 0},
    {"key": "+551140038700", "country": "BR", "category": "telemarketing", "score": 74, "reports": 89, "uniqueReporters": 61, "firstSeen": "2024-02-20T09:00:00Z", "lastSeen": "2026-10-18T16:45:00Z", "confidence": 0.78, "falsePositives": 0},
    {"key": "+5511987654321", "country": "BR", "category": "golpe", "score": 92, "reports": 301, "uniqueReporters": 214, "firstSeen": "2024-03-10T11:00:00Z", "lastSeen": "2026-10-22T08:15:00Z", "confidence": 0.95, "falsePositives": 2},
    {"key": "+5521976543210", "country": "BR", "category": "fraude", "score": 90, "reports": 178, "uniqueReporters": 132, "firstSeen": "2024-04-05T13:00:00Z", "lastSeen": "2026-10-21T10:30:00Z", "confidence": 0.92, "falsePositives": 1},
    {"key": "+5531987651234", "country": "BR", "category": "spam", "score": 68, "reports": 34, "uniqueReporters": 27, "firstSeen": "2024-05-12T08:00:00Z", "lastSeen": "2026-10-19T12:00:00Z", "confidence": 0.65, "falsePositives": 0},
    {"key": "+5511912345678", "country": "BR", "category": "robocall", "score": 71, "reports": 57, "uniqueReporters": 44, "firstSeen": "2024-06-18T15:00:00Z", "lastSeen": "2026-10-20T09:45:00Z", "confidence": 0.72, "falsePositives": 0},
    # Entradas adicionais para completar k-anonimato (prefixos variados)
    *[{"key": f"+551199999{i:04d}", "country": "BR", "category": "telemarketing", "score": 65 + (i % 20), "reports": 10 + i, "uniqueReporters": 5 + (i % 10), "firstSeen": "2024-07-01T00:00:00Z", "lastSeen": "2026-10-01T00:00:00Z", "confidence": 0.5, "falsePositives": 0} for i in range(44)],
]

def generate_keypair():
    """Gera par Ed25519 e salva (dev only)."""
    KEYS_DIR.mkdir(parents=True, exist_ok=True)
    
    private_key = ed25519.Ed25519PrivateKey.generate()
    public_key = private_key.public_key()
    
    # Salva chave privada (PKCS#8, sem criptografia - APENAS DEV)
    with open(DEV_PRIVATE_KEY_PATH, "wb") as f:
        f.write(private_key.private_bytes(
            encoding=Encoding.PEM,
            format=PrivateFormat.PKCS8,
            encryption_algorithm=NoEncryption()
        ))
    
    # Salva chave pública (DER PKCS#8)
    with open(DEV_PUBLIC_KEY_PATH, "wb") as f:
        f.write(public_key.public_bytes(
            encoding=Encoding.DER,
            format=PublicFormat.SubjectPublicKeyInfo
        ))
    
    print(f"✅ Chaves geradas:")
    print(f"   Privada: {DEV_PRIVATE_KEY_PATH} (APENAS DEV - NUNCA COMMITAR)")
    print(f"   Pública: {DEV_PUBLIC_KEY_PATH}")
    
    return private_key, public_key

def load_or_generate_keypair():
    """Carrega chaves existentes ou gera novas."""
    if DEV_PRIVATE_KEY_PATH.exists() and DEV_PUBLIC_KEY_PATH.exists():
        with open(DEV_PRIVATE_KEY_PATH, "rb") as f:
            private_key = serialization.load_pem_private_key(f.read(), password=None)
        with open(DEV_PUBLIC_KEY_PATH, "rb") as f:
            public_key = serialization.load_der_public_key(f.read())
        print(f"✅ Chaves carregadas de {KEYS_DIR}/")
        return private_key, public_key
    else:
        return generate_keypair()

def sign_data(private_key: ed25519.Ed25519PrivateKey, data: bytes) -> str:
    """Assina dados com Ed25519, retorna hex."""
    signature = private_key.sign(data)
    return signature.hex()

def sha256_hex(data: str) -> str:
    """SHA256 hex digest."""
    return hashes.Hash(hashes.SHA256()).update(data.encode()).finalize().hex()

def build_manifest(entries: list, private_key: ed25519.Ed25519PrivateKey) -> dict:
    """Constrói manifest assinado."""
    now = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    entries_json = json.dumps(entries, separators=(",", ":"), ensure_ascii=False)
    
    # Digest do payload
    digest = sha256_hex(entries_json)
    
    # Manifesto para assinar (sem entriesJson)
    manifest_for_signing = {
        "version": VERSION,
        "entries": len(entries),
        "digest": digest,
        "publishedAt": now,
    }
    manifest_json = json.dumps(manifest_for_signing, separators=(",", ":"), ensure_ascii=False)
    
    # Assinatura Ed25519 do manifesto
    signature = sign_data(private_key, manifest_json.encode())
    
    # Chave pública em hex (para verificação no device)
    with open(DEV_PUBLIC_KEY_PATH, "rb") as f:
        public_key_der = f.read()
    public_key_hex = public_key_der.hex()
    
    return {
        "version": VERSION,
        "entries": len(entries),
        "digest": digest,
        "signature": signature,
        "publicKey": public_key_hex,
        "publishedAt": now,
        "entriesJson": entries_json,  # Incluído no asset para verificação
    }

def main():
    print("🔐 Gerando dataset seed assinado (Ed25519)...")
    
    # Carrega/gera chaves
    private_key, public_key = load_or_generate_keypair()
    
    # Prepara entradas
    entries = SEED_ENTRIES[:ENTRIES_COUNT]
    print(f"📦 Entradas: {len(entries)}")
    
    # Constrói manifest
    manifest = build_manifest(entries, private_key)
    
    # Cria diretório de assets
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # Salva dataset_seed.json (manifest + entriesJson)
    seed_path = OUTPUT_DIR / "dataset_seed.json"
    with open(seed_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, separators=(",", ":"), ensure_ascii=False)
    print(f"✅ Dataset seed: {seed_path} ({seed_path.stat().st_size / 1024:.1f} KB)")
    
    # Copia chave pública para assets (para verificação no device)
    import shutil
    shutil.copy2(DEV_PUBLIC_KEY_PATH, OUTPUT_DIR / "dataset_public_key.der")
    print(f"✅ Chave pública copiada para assets: {OUTPUT_DIR / 'dataset_public_key.der'}")
    
    # Verificação rápida
    print("\n🔍 Verificação:")
    print(f"   Version: {manifest['version']}")
    print(f"   Entries: {manifest['entries']}")
    print(f"   Digest:  {manifest['digest'][:16]}...")
    print(f"   Sig:     {manifest['signature'][:16]}...")
    print(f"   PubKey:  {manifest['publicKey'][:16]}...")
    
    # Testa verificação
    manifest_for_verify = {k: v for k, v in manifest.items() if k != "entriesJson" and k != "publicKey"}
    manifest_json = json.dumps(manifest_for_verify, separators=(",", ":"), ensure_ascii=False)
    try:
        public_key.verify(bytes.fromhex(manifest["signature"]), manifest_json.encode())
        print("   ✅ Assinatura VÁLIDA")
    except Exception as e:
        print(f"   ❌ Assinatura INVÁLIDA: {e}")
        sys.exit(1)
    
    print("\n🎉 Dataset seed gerado com sucesso!")
    print(f"   Inclua no APK: {OUTPUT_DIR}/dataset_seed.json + dataset_public_key.der")
    print(f"   SeedWorker carrega na 1ª execução, verifica Ed25519 + SHA256, persiste no Room.")

if __name__ == "__main__":
    main()