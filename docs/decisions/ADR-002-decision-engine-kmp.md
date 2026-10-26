# ADR-002: Decision Engine em Kotlin Multiplatform

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A1, A3

---

## Contexto

O coração do app é o **Decision Engine** (pipeline: Normalize → Emergency → Contacts → Rules → Blacklist → Reputation → Patterns → Campaign → STIR/SHAKEN → Score → Decision). Precisa:

1. Rodar no Android (CallScreeningService, UI, testes)
2. Ser testável unitariamente (JUnit, sem Android)
3. Potencialmente reutilizável no backend (validação, simulação)
4. Determinístico — mesma entrada = mesma saída

---

## Decisão

**Implementar Decision Engine em Kotlin Multiplatform (KMP):**

- `shared/decision-engine/src/commonMain/` — lógica pura (Kotlin stdlib only)
- `shared/decision-engine/src/androidMain/` — implementações Android-specific (se necessário)
- `shared/decision-engine/src/test/` — testes JUnit comuns

**Contrato estável:** `DecisionEngine.decide(EngineInput): CallDecision`

---

## Consequências

### Positivas
- ✅ Single source of truth — mesma lógica no device e nos testes
- ✅ Testes rápidos (JUnit JVM, sem emulador)
- ✅ Compartilhável com backend (Kotlin/JVM ou compilação para JS/WASM futuro)
- ✅ Type-safe, null-safe, exhaustiveness checking
- ✅ Fácil de auditar e validar formalmente

### Negativas
- ❌ Setup KMP inicial (Gradle, targets)
- ❌ Algumas APIs Java/Kotlin stdlib não disponíveis em todos targets (mitigação: `expect`/`actual`)

---

## Estrutura do EngineInput

```kotlin
data class EngineInput(
    val ctx: CallContext,           // Chamada recebida
    val config: DecisionEngineConfig, // Modo + thresholds
    val rules: List<Rule>,          // Regras do usuário
    val allowlist: List<String>,    // Whitelist explícita
    val blacklist: List<String>,    // Blacklist explícita
    val lookupReputation: (String) -> ReputationEntry?, // Função de consulta ao Room
    val recentCalls: List<CampaignSignal>, // Histórico para detecção de campanha
    val contactNumbers: Set<String>,       // Contatos resolvidos localmente
    val previouslyBlocked: Set<String>,    // Números que usuário bloqueou
    val previouslyAllowed: Set<String>     // Números que usuário permitiu
)
```

---

## Pipeline de Decisão (ordem fixa)

1. **Mode OFF** → ALLOW
2. **Emergency** → ALWAYS ALLOW (190, 192, 193, 188, 199)
3. **Contact/Allowlist** → ALLOW
4. **Blacklist** → BLOCK (score 100)
5. **User Rules** (primeira que casa) → action da regra
6. **Brazil 0303** → config.handle0303 (allow/identify/silence/block)
7. **Composite Score** (reputation + campaign + STIR/SHAKEN + history)
8. **Aggressive Unknown International** → boost score se configurado
9. **Thresholds** → ALLOW/WARN/SILENCE/BLOCK
10. **Fail-safe** → qualquer exceção → ALLOW

---

## Referências

- [Kotlin Multiplatform docs](https://kotlinlang.org/docs/multiplatform.html)
- [SDD §5.2 Spam Shield](./SDD.md#52-spam-shield-proteção)