# ADR-001: Android-first, Local-first Architecture

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0 (Tech Lead), A1 (Android Core), A4 (Backend)

---

## Contexto

O Conexus Caller ID deve funcionar **100% offline** para bloqueio de chamadas. A identificação e decisão de bloqueio/permite não podem depender de conectividade de rede, pois:

1. Chamadas chegam a qualquer momento (incluindo sem dados/Wi-Fi)
2. Latência de rede > 100ms violaria o timeout do Android CallScreeningService
3. Privacidade: agenda do usuário nunca deve sair do dispositivo

---

## Decisão

**Arquitetura Local-first com sincronização assíncrona:**

1. **Decisão de screening 100% local** — Decision Engine roda no device (Kotlin), usando Room DB com dataset seed + deltas assinados
2. **API comunitária só para:**
   - Consulta k-anônima (prefixo) quando online — **opcional**, fallback para local
   - Envio de denúncias — **opcional**, com PoW, nunca bloqueia UX
   - Sync de dataset (deltas assinados Ed25519) — WorkManager periódico
3. **Nenhum upload de contatos** — contatos resolvidos localmente via `ContactsContract`, apenas nomes usados no histórico local
4. **Fail-safe** — qualquer erro no pipeline → `ALLOW` (nunca bloquear por falha)

---

## Consequências

### Positivas
- ✅ Funciona offline total (avião, área rural, sem plano de dados)
- ✅ Latência p95 < 50ms local (bem dentro do timeout 2s do Android)
- ✅ Privacidade por design — agenda nunca sai do device
- ✅ Resiliente a falhas de backend/rede
- ✅ Conforme LGPD e Play Policy (sem coleta indiscriminada)

### Negativas
- ❌ Dataset local pode ficar desatualizado (mitigado: sync periódico + k-anon fallback)
- ❌ Tamanho do APK aumenta (~2-5 MB dataset seed)
- ❌ Complexidade de sync assíncrono (WorkManager, assinaturas, anti-rollback)

### Riscos
- OEMs podem restringir `ROLE_CALL_SCREENING` (mitigação: fallback Call Directory iOS / notificação persistente)
- Play Console service account 403 (mitigação: conta dedicada + verificação prévia)

---

## Alternativas Consideradas

| Alternativa | Por que não |
|-------------|-------------|
| Cloud-first (decisão na API) | Latência, privacidade, falha offline |
| Híbrido síncrono (API decide, local fallback) | Complexidade de timeout, race conditions |
| Upload de contatos para matching server-side | Viola privacidade, LGPD, Play Policy |

---

## Implementação

- `DecisionEngine.kt` (Kotlin multiplatform) — lógica pura, testável
- `CallerIdScreeningService.kt` — `CallScreeningService` nativo, chama DecisionEngine via coroutine
- `SeedWorker` / `DeltaSyncWorker` — WorkManager para init + sync periódico
- `Room` — cache local (reputation, history, blocklist, settings)

---

## Referências

- [Android CallScreeningService docs](https://developer.android.com/reference/android/telecom/CallScreeningService)
- [LGPD Art. 7](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/L13709.htm)
- [Play Console Device and Network Abuse Policy](https://play.google.com/about/developer-content-policy/)