# ADR-004: Privacidade — HMAC Phone Hashes, No Contact Upload, PoW Reports

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A4, A5

---

## Contexto

Requisitos de privacidade inegociáveis:

1. **Agenda nunca sai do device** — LGPD, Play Policy, confiança do usuário
2. **Consultas não revelam números completos** — k-anonimato + HMAC
3. **Denúncias não permitem spam/enumeração** — PoW + rate limit + anonimato
4. **Dados sensíveis criptografados** — SQLCipher local, TLS 1.3 remoto

---

## Decisão

### 1. Hash HMAC para Números (Backend)

- **Nunca armazenar números em claro** no Postgres
- `phone_hmac = HMAC-SHA256(phone_e164, HMAC_SECRET)[:16]` (16 chars hex = 64 bits)
- `HMAC_SECRET` gerado na cerimônia, armazenado em vault, rotacionável
- Mesmo número → mesmo HMAC (permite join/agregação)
- Sem segredo → impossível reverter (espaço 2^64, mas segredo desconhecido)

### 2. k-Anonimato na Consulta (Lookup)

- Cliente envia **prefixo 6-8 dígitos** do número (ex.: `55119999`)
- Backend retorna **todas entradas** cujo HMAC prefixo casa (bucket)
- Mínimo **100 entradas por bucket** (config `K_ANONYMITY_MIN_BUCKET_SIZE`)
- Se bucket < 100 → fundir com prefixo adjacente ou retornar erro

### 3. Denúncias com Proof-of-Work (PoW)

```http
POST /v1/reports
X-PoW-Nonce: <nonce>
Content-Type: application/json

{
  "phone_hash": "abc123...",
  "category": "telemarketing",
  "occurred_at": "2026-10-24T15:00:00Z",
  "pow_difficulty": 4,
  "client_id": "hmac_device_salt..."
}
```

- **Hashcash-style**: `SHA256(nonce + canonical_data)` deve ter `difficulty` leading zeros
- Dificuldade ajustável (padrão 4, máx 8) — aumenta custo de spam
- Nonce válido uma vez (backend armazena nonces usados por 24h — anti-replay)
- `client_id = HMAC(device_id, REPORT_SALT)` — anônimo, permite anti-abuso (unique reporters)

### 4. Rate Limiting

| Endpoint | Limite | Janela |
|----------|--------|--------|
| `/lookup` | 30 req/min | IP + API Key |
| `/reports` | 10 req/min | IP + API Key |
| Demais | 60 req/min | IP |

### 5. Criptografia Local

- **Room + SQLCipher** — chave derivada de `MasterKey` (Android Keystore)
- Chaves de API, tokens, configurações sensíveis → `EncryptedSharedPreferences` / Keystore
- Backup exportado → JSON **não criptografado** (usuário escolhe onde salvar), mas import valida schema

### 6. Contestação e Exclusão

- **Contestação**: 90 dias após moderação `approved` → `contested` → revisão humana
- **Exclusão LGPD**: Endpoint `/v1/user/data` (auth Conta Conexus) → apaga `user_blocklists`, `spam_reports` (anonimiza), logs
- **Retenção**: Denúncias 2 anos, Scores até contestação expirar, Logs 90 dias

---

## Consequências

### Positivas
- ✅ Agenda 100% local — zero risco de vazamento
- ✅ k-anonimato real — bucket mínimo 100 impede reidentificação trivial
- ✅ PoW econômico — custo computacional para spammers, desprezível para usuário legítimo
- ✅ HMAC não reversível sem segredo — mesmo vazamento de DB não expõe números
- ✅ Auditoria completa — `audit_events` imutável para moderação/contestação

### Negativas
- ❌ HMAC colisão teórica (64 bits = 2^32 birthday bound) — aceitável para 10M números
- ❌ PoW consome bateria/CPU no device (mitigação: difficulty baixa, só em denúncia)
- ❌ k-anonimato vaza prefixo geográfico (DDD) — risco baixo, benefício alto

---

## Referências

- [LGPD Art. 7, 18](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/L13709.htm)
- [k-anonymity (Sweeney, 2002)](https://dataprivacylab.org/projects/kanonymity/)
- [Hashcash (Back, 1997)](http://www.hashcash.org/papers/hashcash.pdf)
- [SDD §7 Segurança, Privacidade e Conformidade](./SDD.md#7-segurança-privacidade-e-conformidade)