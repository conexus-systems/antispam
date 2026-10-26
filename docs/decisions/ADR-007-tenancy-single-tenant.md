# ADR-007: Tenancy — Single-Tenant App (No Multi-Tenancy no APK)

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A4

---

## Contexto

O Conexus Caller ID é um **app de consumidor** (B2C), não um SaaS multi-tenant. Cada instalação é independente:

1. Um usuário = um device = uma instalação
2. Dados locais (Room) pertencem ao usuário
3. Sync opcional via Conta Conexus (single user account)
4. Backend serve múltiplos usuários, mas app não tem conceito de "tenant"

---

## Decisão

**Single-tenant no APK, multi-user no Backend:**

### App (Android)
- **Zero multi-tenancy** — cada device roda sua instância isolada
- Room DB local = dados do usuário (history, blocklist, settings, reputation cache)
- Conta Conexus = opcional, para backup/sync/denúncias identificadas
- Nenhum `tenant_id` no código do app

### Backend (FastAPI)
- **Multi-user** — `user_id` (UUID Conta Conexus) em `user_blocklists`, `audit_events`
- `phone_hashes`, `reputation_scores`, `business_listings` = **globais** (compartilhados)
- `spam_reports` = globais, mas com `reporter_hash` para anti-abuso
- Row-level security via `user_id` onde aplicável (blocklist sync)

---

## Modelo de Dados

```sql
-- Globais (sem tenant_id)
CREATE TABLE phone_hashes (...);
CREATE TABLE business_listings (...);
CREATE TABLE reputation_scores (...);
CREATE TABLE spam_reports (...);  -- reporter_hash = HMAC(device_id, salt)
CREATE TABLE dataset_manifests (...);

-- Por usuário (Conta Conexus)
CREATE TABLE user_blocklists (
    user_id UUID NOT NULL,  -- Conta Conexus
    phone_hmac CHAR(16) NOT NULL,
    action VARCHAR(10) NOT NULL,  -- block/allow
    source VARCHAR(20) NOT NULL,  -- local/remote/rule
    ...
);

CREATE TABLE audit_events (
    actor_id UUID,  -- admin/moderador
    ...
);
```

---

## Sync Conta Conexus (Opcional)

```kotlin
// Apenas se usuário logado na Conta Conexus
suspend fun syncBlocklists() {
    val localBlocks = blocklistDao.getBlocked().first()
    val localAllows = blocklistDao.getAllowed().first()
    
    // Upload
    api.uploadBlocks(localBlocks.map { it.toRemote() })
    api.uploadBlocks(localAllows.map { it.toRemote() })
    
    // Download + merge
    val remote = api.downloadBlocks()
    remote.forEach { remoteBlock ->
        val local = blocklistDao.get(remoteBlock.phoneHash)
        val merged = merge(local, remoteBlock)  // last-write-wins + source priority
        blocklistDao.upsert(merged)
    }
}
```

**Conflict resolution:** `source` priority: `remote` > `rule` > `local`. Timestamp tiebreaker.

---

## Consequências

### Positivas
- ✅ Simplicidade — app não gerencia tenants, schemas, migrações multi-tenant
- ✅ Performance — Room local sem joins/filtros de tenant
- ✅ Privacidade — dados sensíveis ficam no device por padrão
- ✅ Offline-first — funciona 100% sem conta/servidor
- ✅ Backend escalável — tabelas globais compartilhadas, apenas blocklist por user

### Negativas
- ❌ Não suporta "organizações" ou "famílias" compartilhando blocklist (futuro: feature flag)
- ❌ Backup/restore só via Conta Conexus (não multi-device automático sem conta)

---

## Referências

- [SDD §7 Tenancy](./SDD.md#7-segurança-privacidade-e-conformidade)
- [Conexus Multi-tenancy patterns](../architecture/MULTI_TENANCY.md)