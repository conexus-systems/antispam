# Arquitetura Kotlin: núcleo e API para as telas

Escopo: `native/android/app/src/main/java/br/com/conexus/antispam/`. As telas (`ui/`) só falam com `domain/`; nunca com DAOs ou entidades Room.

```
ui/  ->  domain/ (repositórios + ScreeningPipeline)  ->  database/ (Room)   decision/ (DecisionEngine, JVM puro)
screening/CallerIdScreeningService  ->  ScreeningPipeline (o MESMO da tela Verificar e da Home)
```

## Como obter (Hilt)

Todos são `@Singleton` com `@Inject constructor`; basta injetar em um `@HiltViewModel`:

```kotlin
@HiltViewModel
class HistoryViewModel @Inject constructor(private val history: HistoryRepository) : ViewModel() {
    val items = history.observe(HistoryFilter.ALL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

Modelos de UI (`domain/Models.kt`): `HistoryItem`, `HistoryCounts`, `ListItem`, `RuleItem`, `ReportCategory`, `DatasetInfo`, `ScreeningRequest`, `ScreeningResult`, `ListStatus`. Rótulos PT-BR: `domain/DecisionLabels.kt` (`CallAction.label()`/`pastLabel()`, `DecisionReason.label()`, `Confidence.label()`).

## Repositórios

| Classe | Assinaturas |
|---|---|
| `HistoryRepository` | `observe(filter: HistoryFilter = ALL, limit = 500): Flow<List<HistoryItem>>` · `observeItem(id): Flow<HistoryItem?>` · `suspend get(id)` · `observeCounts(): Flow<HistoryCounts>` (Home) · `suspend delete(id)` · `suspend clear()` · `suspend purgeExpired(days): Int` |
| `ListRepository` | `observeBlocked()` / `observeAllowed(): Flow<List<ListItem>>` · `suspend block(raw, note?, source="local"): ListResult` · `suspend allow(raw, note?, source): ListResult` (também é o "Desbloquear / Não é spam") · `suspend remove(rawOrE164)` · `suspend undo(added: ListResult.Added)` · `suspend restore(entry)` · `suspend status(raw): ListStatus` · `normalize(raw): String?` |
| `RulesRepository` | `observe(): Flow<List<RuleItem>>` (já em ordem de prioridade) · `suspend create(name, kind, value, action, enabled=true): RuleResult` · `suspend update(id, name, value, action): RuleResult` · `suspend setEnabled(id, enabled)` · `suspend delete(id): Boolean` (false para regra de sistema) · `suspend reorder(orderedIds)` · `normalizeValue(kind, raw): String?` |
| `SettingsRepository` | `observe(): Flow<UserSettings>` · `suspend get()` · `observeDataset(): Flow<DatasetInfo?>` · setters: `setMode(ProtectionMode)`, `setCustomThresholds(warn, block, silence)`, `setHandle0303("allow"/"identify"/"silence"/"block")`, `setBlockHidden`, `setBlockInternational`, `setBlockUnknownInAggressive`, `setNotificationsEnabled`, `setRejectBlockedCalls`, `setTheme`, `setAccent`, `setReduceMotion`, `setOnboardingDone`, `setHistoryRetentionDays`, `setConsentShareReports` · `suspend update { it.copy(...) }` |
| `ReportRepository` | `suspend report(raw, category: ReportCategory): ReportResult` (`Saved(receipt)` / `AlreadyReported` / `Invalid` / `Emergency`) · `suspend undo(receipt)` |
| `PrivacyRepository` | `suspend eraseAllLocalData()` (RF-PRIV-01; recria os padrões; a UI deve voltar ao onboarding) |
| `VerifyNumberUseCase` | `suspend operator fun invoke(raw, presentation = UNKNOWN): VerifyOutcome` (`Ready(result)` / `Invalid(message)`) |
| `ScreeningPipeline` | `suspend evaluate(request): ScreeningResult` · `suspend record(result, simulated=false): HistoryItem` · `failSafe(request, detail?)` |

Regras de comportamento que a UI deve saber:

- **Listas exclusivas.** `block` de um número permitido o move (e vice-versa). `ListResult.Added.previous` guarda o estado anterior; passe o `Added` a `undo()` para a barra "Desfazer". Resultados: `Invalid` ("Número inválido"), `Duplicate` ("Já está na lista"), `Emergency` (190/192/193... nunca entram).
- **Regras de sistema** (ids fixos em `SystemRules`): 0303 (ligada, SILENCE), ocultos e internacionais (desligadas, BLOCK). Não se apagam. Os ajustes `setHandle0303` / `setBlockHidden` / `setBlockInternational` atualizam essas regras; ligar um `Switch` na tela de Regras (`setEnabled`) tem o mesmo efeito sobre o motor. Regras de usuário valem antes das de sistema.
- **Denúncia** não depende de peso do dataset: o número vai para bloqueados (`source="report"`, nota = categoria), a reputação local é criada/atualizada e as linhas do histórico daquele número ficam `reported=true`. Nada é enviado (RF-DENUN-02 é opt-in futuro).
- **Verificar/simulador não gravam nada**: não criam bloqueio nem aparecem no histórico. "Bloquear número" e "Denunciar" após o resultado chamam `ListRepository.block` / `ReportRepository.report`.
- Formato de `RuleItem.value` por tipo: ver KDoc de `RulesRepository`.

## Verificar e simulador da Home

```kotlin
@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val verify: VerifyNumberUseCase,
    private val lists: ListRepository,
    private val reports: ReportRepository,
) : ViewModel() {
    suspend fun check(text: String) = when (val o = verify(text)) {
        is VerifyOutcome.Invalid -> showError(o.message)
        is VerifyOutcome.Ready -> show(o.result)   // headline, actionLabel, riskLabel, explanation, listStatus
    }
    // Home: verify("", CallPresentation.RESTRICTED) simula chamada oculta.
}
```

`ScreeningResult`: `decision` (CallDecision completo), `displayNumber`, `headline` ("Provável spam"), `actionLabel` ("Bloquear"), `riskLabel`, `explanation: List<String>` (PT-BR), `listStatus` (para escolher "Bloquear" x "Desbloquear").

## Serviço de triagem

`CallerIdScreeningService`: só chamadas recebidas; `ScreeningPipeline.evaluate` sob `withTimeout(2500 ms)` (estouro = ALLOW/FAILSAFE); grava histórico (limite 1000 ms) **antes** de responder; notifica se `notificationsEnabled` e permitido; responde com `ResponsePlan.from(result)`:

| Ação | CallResponse |
|---|---|
| BLOCK | `disallow=true`, `reject=rejectBlockedCalls`, `skipNotification=true`, `skipCallLog=false` (a chamada continua no log do sistema; o histórico do app é gravado de qualquer forma) |
| SILENCE | `silenceCall=true` |
| WARN / ALLOW | normal (WARN notifica) |
| Emergência | sempre normal |

Sem rede no caminho. `contactNumbers` fica vazio no MVP (sem `READ_CONTACTS`).

## Banco (Room v2)

Tabelas: `user_settings`, `blocklist_entries` (+`note`), `rules`, `history_entries` (+`confidence`, `explanation`, `reported`; índices `at DESC`, `number`), `reputation_entries`, `dataset_meta`. `RoomDatabase.Callback.onCreate/onOpen` insere configurações, `dataset_meta` (source=seed) e regras padrão (`DefaultData`). `fallbackToDestructiveMigration` mantido até a v1 pública; `exportSchema=false` até lá (exigiria `room.schemaLocation` no `build.gradle.kts`).

## Trabalho em segundo plano

`AntiSpamApplication.onCreate` agenda (unique work, KEEP): `SeedWorker` (semente embutida em código, sem asset), `DeltaSyncWorker` (6 h, com rede; ainda stub) e `HousekeepingWorker` (diário, retenção do histórico). `BootReceiver` foi removido: o WorkManager persiste os agendamentos.

## Testes

`app/src/test/.../domain/` usa DAOs em memória (`Fakes.kt`, `TestEnv`): os DAOs do Room são interfaces, então tudo roda na JVM, sem Android.
