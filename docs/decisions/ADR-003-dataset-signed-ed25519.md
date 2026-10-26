# ADR-003: Dataset Assinado Ed25519, Delta Updates, Anti-Rollback

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A1, A4, A5

---

## Contexto

O dataset de reputação (centenas de milhares de números) precisa ser:

1. **Incluso no APK** (seed) para funcionamento offline imediato
2. **Atualizável** via backend sem re-release do app
3. **Verificável** — device deve confirmar autenticidade e integridade
4. **Resistente a rollback** — versão anterior não pode ser imposta
5. **Eficiente** — deltas incrementais, não download full

---

## Decisão

**Formato de dataset assinado com Ed25519 + deltas incrementais:**

### Seed (no APK)
```
assets/
  dataset_seed.json          # Manifest + entries (JSON)
  dataset_public_key.der     # Ed25519 public key (PKCS#8)
```

### Manifest (assinado)
```json
{
  "version": 42,
  "entries": 127342,
  "digest": "sha256:abc123...",
  "signature": "ed25519:xyz789...",
  "entriesJson": "[{...}, {...}]",
  "publicKey": "ed25519:pubkey...",
  "publishedAt": "2026-10-24T12:00:00Z"
}
```

### Delta (API)
```
GET /v1/datasets/latest          → Manifest mais recente
GET /v1/datasets/{version}/delta → Delta entries (JSON array)
```

### Verificação no Device (SeedWorker / DeltaSyncWorker)
1. Carregar manifest
2. Verificar assinatura Ed25519 (chave pública hardcoded no APK)
3. Verificar digest SHA256 do `entriesJson` / delta
4. Verificar `version > currentVersion` (anti-rollback)
5. Aplicar entries no Room (upsert por `key`)
6. Persistir `datasetVersion` no `UserSettings`

---

## Consequências

### Positivas
- ✅ Autenticidade criptográfica — só chaves privadas autorizadas publicam
- ✅ Integridade — qualquer bit alterado invalida assinatura/digest
- ✅ Anti-rollback — versão monotônica, device rejeita versões antigas
- ✅ Eficiência — deltas pequenos (apenas mudanças), seed apenas no primeiro run
- ✅ Auditoria — `DatasetManifest` no Postgres com cadeia `previous_version`

### Negativas
- ❌ Cerimônia de chave Ed25519 produção (HSM, split knowledge)
- ❌ Rotação de chave futura requer migração cuidadosa
- ❌ Tamanho do seed no APK (~2-5 MB)

---

## Cerimônia de Chave Produção

1. Gerar par Ed25519 em HSM offline (`ed25519_private.pem`, `ed25519_public.der`)
2. Public key → commit no repo (assets/dataset_public_key.der)
3. Private key → armazenar em HSM / vault (nunca no repo/CI)
4. Script de publicação (`scripts/publish_dataset.py`) usa HSM para assinar
5. Backup da chave privada em cofre físico (split entre 3 pessoas)

---

## Formato Delta

```json
[
  {
    "key": "+5511999999999",
    "country": "BR",
    "category": "telemarketing",
    "score": 78,
    "reports": 142,
    "uniqueReporters": 96,
    "firstSeen": "2026-01-15T10:00:00Z",
    "lastSeen": "2026-10-20T14:30:00Z",
    "confidence": 0.85,
    "falsePositives": 0
  }
]
```

Merge strategy: `upsert` por `key`, `score = max(existing, new)`, `reports += new`, `uniqueReporters = max(existing, new)`, `confidence` recalculado.

---

## Referências

- [Ed25519 RFC 8032](https://datatracker.ietf.org/doc/html/rfc8032)
- [SDD §11 Dataset Format](./SDD.md#11-formato-de-dataset-aberto)
- [Threat Model §6 Supply Chain](./THREAT_MODEL.md)