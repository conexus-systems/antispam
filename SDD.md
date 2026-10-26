# SDD — Conexus Caller ID

**Versão:** 1.0  
**Data:** 24 de outubro de 2026  
**Projeto:** Conexus Caller ID (novo repositório)  
**Orquestrador:** A0 (Tech Lead)

---

## 1. Configuração Escolhida

| Item | Escolha |
|------|---------|
| **Plataforma** | Android primeiro |
| **Origem dos dados** | Híbrido: dados públicos + base comunitária |
| **Publicação** | Pipeline CI/CD, publicação sempre protegida por aprovação humana |
| **Backend** | Python + FastAPI + PostgreSQL |

---

## 2. Objetivo do Produto

Desenvolver um aplicativo Android **privacy-first** capaz de:

- Identificar chamadas de números conhecidos (dados públicos + base comunitária moderada)
- Alertar sobre chamadas suspeitas e possíveis golpes (score de risco)
- Bloquear números e categorias de chamadas indesejadas (local + sync opcional)
- Permitir pesquisar números de telefone (reverse lookup)
- Exibir informações comerciais públicas associadas a números
- Permitir que usuários denunciem spam (com controles anti-abuso)
- Manter lista local de chamadas identificadas (respeitando permissões Android)
- **Princípio fundamental:** não enviar a agenda telefônica inteira do usuário para um servidor por padrão. Identificação funciona com dados públicos, fontes autorizadas, denúncias moderadas e consultas sob demanda (k-anônima).

---

## 3. Stack Tecnológica

| Camada | Tecnologia |
|--------|------------|
| **App Android** | Kotlin + Jetpack Compose (Material 3 / Fluent tokens) |
| **Integração chamadas** | Android Telecom `CallScreeningService` (Android 10+) |
| **Persistência local** | Room (SQLite) + SQLCipher para dados sensíveis |
| **Backend API** | Python 3.12 + FastAPI |
| **Banco de dados** | PostgreSQL 16 + pgvector (embeddings futuros) |
| **Cache / Rate limit** | Redis 7 (Upstash ou self-hosted) |
| **Workers assíncronos** | Python + Celery + Redis |
| **Infraestrutura** | Docker + Terraform (Linode LKE) + GHCR |
| **Observabilidade** | OpenTelemetry → Grafana + Sentry |
| **Testes** | pytest (backend), JUnit + Compose Testing + Espresso (Android), Playwright E2E |
| **CI/CD** | GitHub Actions (Blacksmith 2 vCPU) → manual approval → production |

---

## 4. Arquitetura Geral

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID APP                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ CallScreening│  │  Compose UI  │  │   Local Room DB      │  │
│  │   Service    │  │  (Caller ID, │  │  (cache, blocklist,  │  │
│  │  (Kotlin)    │  │   History,   │  │   reports, settings) │  │
│  └──────┬───────┘  │   Settings)  │  └──────────┬───────────┘  │
│         │          └──────┬───────┘             │              │
│         ▼                 ▼                      ▼              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Repository / Data Layer (Kotlin)             │  │
│  │  - Local-first: Room DB                                   │  │
│  │  - Remote: Retrofit → FastAPI (k-anonymous lookup)       │  │
│  │  - Sync: WorkManager (delta updates, signed datasets)    │  │
│  └────────────────────────────┬──────────────────────────────┘  │
└───────────────────────────────│─────────────────────────────────┘
                                │ HTTPS / mTLS
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      BACKEND (FastAPI)                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐  │
│  │  /lookup    │ │  /reports   │ │  /reputation│ │  /blocks  │  │
│  │  (k-anon)   │ │  (PoW+mod)  │ │  (scores)   │ │  (sync)   │  │
│  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └─────┬────┘  │
│         │               │               │               │       │
│         ▼               ▼               ▼               ▼       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Reputation Engine (Python)                   │  │
│  │  - Score 0-100, teto por rede, evidências                │  │
│  │  - Publicação: Σw≥3, 48h, ≥3 redes, ≥50% peso maduro     │  │
│  │  - Contestações + moderação auditada, proteção 90 dias   │  │
│  └────────────────────────────┬──────────────────────────────┘  │
│                               │                                 │
│                               ▼                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              PostgreSQL + Redis                           │  │
│  │  - phone_hashes (HMAC), business_listings, spam_reports  │  │
│  │  - reputation_scores, user_blocklists, audit_events      │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Módulos Funcionais (App Android)

### 5.1 Caller ID (Identificação)
- **Fontes:** Dados públicos (CNPJ, Anatel, listas oficiais), base comunitária moderada, denúncias locais
- **Exibição:** Nome + categoria (ex.: "Banco XYZ — Telemarketing") + score de confiança
- **Sem evidência:** Mostrar apenas número formatado (ex.: "+55 11 9999-9999")

### 5.2 Spam Shield (Proteção)
- **Score 0–100** → ação: ALLOW / WARN / SILENCE / BLOCK
- **Modos:** OFF / BASIC / BALANCED / AGGRESSIVE / CUSTOM
- **Regras usuário:** prefixo, DDD, regex, horário, ocultos, internacionais, não-contatos
- **Nunca bloquear:** emergência (190, 192, 193, 188, 199), contatos, allowlist
- **Fail-safe:** erro → ALLOW

### 5.3 Reverse Lookup (Pesquisa)
- **Entrada:** número digitado ou do histórico
- **Consulta:** k-anônima (prefixo 6–8 dígitos) → API
- **Resultado:** informações públicas + reputação comunitária (se score ≥ threshold)

### 5.4 Community Intelligence
- **Denúncia local:** imediata, alimenta Room DB
- **Envio API:** opcional, com PoW (proof-of-work), nonce anti-replay, rate limit
- **Moderação:** fila, revisão, contestação com SLA 90 dias
- **Anti-abuso:** teto por IP/device, denunciantes únicos, recência, falsos positivos

### 5.5 Onboarding & Permissões
- **Tela única PT-BR:** explica funcionamento offline, agenda não enviada, botão "Conceder permissão" → `ROLE_CALL_SCREENING`
- **Fallback Expo Go:** modo demonstração com simulador

### 5.6 Settings & Controle
- Modo de proteção, regras, allowlist/blocklist, backup local (JSON), export/import
- Tema (system/dark/light), cor de destaque, reduzir movimento
- Conta Conexus (opcional): backup nuvem, denúncias identificadas, doações

---

## 6. Especificação da API (FastAPI)

### 6.1 Endpoints

| Método | Endpoint | Finalidade | Auth |
|--------|----------|------------|------|
| GET | `/v1/lookup/{phone_prefix}` | Consulta k-anônima (prefixo 6–8 dígitos) | API Key opcional |
| POST | `/v1/reports` | Denunciar spam (PoW, nonce, k-anon) | API Key + PoW |
| GET | `/v1/reputation/{phone_hash}` | Obter classificação (HMAC) | API Key |
| GET | `/v1/blocks` | Listar bloqueios do usuário | Bearer (Conta Conexus) |
| POST | `/v1/blocks` | Adicionar bloqueio (sync) | Bearer |
| DELETE | `/v1/blocks/{id}` | Remover bloqueio | Bearer |
| GET | `/v1/datasets/latest` | Manifest do dataset assinado (Ed25519) | Público |
| GET | `/v1/datasets/{version}/delta` | Delta assinado para sync | Público |
| GET | `/v1/health` | Health check | Público |

### 6.2 Contratos Principais

```python
# Lookup k-anônimo (request)
class LookupRequest(BaseModel):
    prefix: str          # 6-8 dígitos (ex.: "55119999")
    max_results: int = 50

# Lookup response
class ReputationItem(BaseModel):
    phone_hash: str      # HMAC-SHA256(phone, secret)[:16]
    country: str
    category: str        # spam, telemarketing, fraud, robocall, etc.
    score: int           # 0-100
    confidence: float    # 0.0-1.0
    last_seen: datetime

class LookupResponse(BaseModel):
    items: List[ReputationItem]
    dataset_version: int

# Report (com PoW)
class ReportRequest(BaseModel):
    phone_hash: str
    category: str
    occurred_at: datetime
    pow_nonce: str       # Proof-of-work nonce
    pow_difficulty: int  # Dificuldade atual
    client_id: str       # HMAC(device_id, salt) - anônimo

# Dataset manifest (assinado Ed25519)
class DatasetManifest(BaseModel):
    version: int
    entries: int
    digest: str          # SHA256 do delta
    signature: str       # Ed25519(manifest_json)
    public_key: str      # Chave de verificação
    published_at: datetime
```

### 6.3 Regras de Negócio Backend
- **Normalização:** E.164 obrigatório (`+5511999999999`)
- **Rate limit:** 30 req/min/IP (lookup), 10 req/min/IP (report)
- **PoW:** Hashcash-style (SHA256 nonce com leading zeros), dificuldade ajustável
- **k-anonimato:** mínimo 100 entradas por prefixo consultado
- **Publicação dataset:** apenas após moderação + critérios de peso
- **Anti-rollback:** versão monotônica, assinatura Ed25519, digest SHA256

---

## 7. Modelo de Dados (PostgreSQL)

```sql
-- Números com HMAC (não reversível sem segredo)
CREATE TABLE phone_hashes (
    id              BIGSERIAL PRIMARY KEY,
    phone_hmac      CHAR(16) NOT NULL,        -- HMAC-SHA256(phone, secret)[:16]
    country         CHAR(2) NOT NULL DEFAULT 'BR',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (phone_hmac)
);

-- Listagens comerciais verificadas
CREATE TABLE business_listings (
    id              BIGSERIAL PRIMARY KEY,
    phone_hmac      CHAR(16) NOT NULL REFERENCES phone_hashes(phone_hmac),
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL,     -- banco, operadora, entrega, etc.
    source          VARCHAR(100) NOT NULL,    -- cnpj, anatel, official, etc.
    source_url      TEXT,
    verified_at     TIMESTAMPTZ NOT NULL,
    confidence      NUMERIC(3,2) NOT NULL,    -- 0.00-1.00
    UNIQUE (phone_hmac, source)
);

-- Denúncias de spam
CREATE TABLE spam_reports (
    id              BIGSERIAL PRIMARY KEY,
    phone_hmac      CHAR(16) NOT NULL REFERENCES phone_hashes(phone_hmac),
    category        VARCHAR(30) NOT NULL,     -- spam, telemarketing, fraud, etc.
    reporter_hash   CHAR(16) NOT NULL,        -- HMAC(device_id, salt)
    occurred_at     TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending', -- pending, approved, rejected, contested
    moderated_at    TIMESTAMPTZ,
    moderator_id    BIGINT,
    contest_deadline TIMESTAMPTZ,             -- +90 dias para contestação
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Scores de reputação agregados
CREATE TABLE reputation_scores (
    phone_hmac      CHAR(16) PRIMARY KEY REFERENCES phone_hashes(phone_hmac),
    score           SMALLINT NOT NULL,        -- 0-100
    confidence      NUMERIC(3,2) NOT NULL,    -- 0.00-1.00
    total_reports   INTEGER NOT NULL DEFAULT 0,
    unique_reporters INTEGER NOT NULL DEFAULT 0,
    weight_sum      NUMERIC(6,2) NOT NULL DEFAULT 0,  -- Σw por rede
    mature_weight   NUMERIC(6,2) NOT NULL DEFAULT 0,  -- peso redes maduras (≥50%)
    last_updated    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (score BETWEEN 0 AND 100)
);

-- Bloqueios por usuário (sync opcional via Conta Conexus)
CREATE TABLE user_blocklists (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL,            -- Conta Conexus
    phone_hmac      CHAR(16) NOT NULL REFERENCES phone_hashes(phone_hmac),
    action          VARCHAR(10) NOT NULL,     -- block, allow
    source          VARCHAR(20) NOT NULL,     -- local, remote, rule
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, phone_hmac, action)
);

-- Auditoria administrativa
CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        UUID,                     -- admin/moderador
    action          VARCHAR(50) NOT NULL,     -- approve_report, reject_report, publish_dataset, etc.
    target_type     VARCHAR(30) NOT NULL,     -- report, dataset, user, etc.
    target_id       VARCHAR(100) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Manifests de dataset publicados
CREATE TABLE dataset_manifests (
    version         INTEGER PRIMARY KEY,
    entries_count   INTEGER NOT NULL,
    digest_sha256   CHAR(64) NOT NULL,
    signature_ed25519 CHAR(128) NOT NULL,     -- hex
    public_key_ed25519 CHAR(64) NOT NULL,     -- hex
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    previous_version INTEGER REFERENCES dataset_manifests(version)
);
```

---

## 8. Segurança, Privacidade e Conformidade

| Requisito | Implementação |
|-----------|---------------|
| **LGPD / Play Policy** | Consentimento granular, finalidade documentada, exclusão sob demanda |
| **Agenda do usuário** | Nunca enviada; contatos resolvidos localmente no device |
| **Criptografia** | TLS 1.3 (mTLS backend), SQLCipher (Room), segredos no Keystore/Keymaster |
| **Anonimato consultas** | k-anônimo (prefixo) + HMAC no backend; PoW anti-spam |
| **Contestação** | SLA 90 dias, trilha de auditoria imutável |
| **Retenção** | Denúncias: 2 anos; Scores: até contestação expirar; Logs: 90 dias |
| **Assinatura dataset** | Ed25519 (chave produção em cerimônia HSM, dev key no repo) |
| **Anti-enumeração** | Rate limit, PoW, prefixo mínimo, hash prefix no retorno |

---

## 9. Plano de Execução (Fases)

### Fase 1 — MVP Android (M1–M2) — **4–6 semanas**
| Sprint | Entregáveis | Critérios de Aceite |
|--------|-------------|---------------------|
| 1 | Projeto Kotlin + Compose, navegação, tema Fluent, onboarding PT-BR, permissão Call Screening | Builda, roda no emulador, onboarding concede papel |
| 2 | `CallScreeningService` nativo, bridge JS↔Kotlin, Room DB (cache, blocklist, history), decision engine local | Bloqueia/permite chamadas simuladas, emergency nunca bloqueado |
| 3 | Dataset seed assinado (Ed25519) no APK, WorkManager init no boot, sync delta | Dataset carrega na 1ª execução, versão registrada |
| 4 | UI: Home (simulador), Histórico (desbloquear/permitir), Settings (modos, regras, listas, backup, aparência) | Todos cenários Gherkin cobertos por testes unit/integration |
| 5 | Testes em aparelho real (Pixel + Samsung), performance <100ms p95, bateria, TalkBack | Zero crashes, 60fps UI, acessibilidade AA |

### Fase 2 — Backend API (M3) — **3–4 semanas**
| Sprint | Entregáveis | Critérios de Aceite |
|--------|-------------|---------------------|
| 1 | FastAPI + PostgreSQL + Redis, migrações, health, OpenAPI spec | `docker compose up` sobe tudo, docs Swagger |
| 2 | `/lookup` k-anônimo, `/reports` com PoW, rate limit, HMAC | Load test 1k RPS, PoW valida, rate limit bloqueia |
| 3 | Reputation Engine (score, teto por rede, publicação Σw≥3, 48h, ≥3 redes, ≥50% maduro) | Dataset publicado assinado Ed25519 |
| 4 | `/reputation`, `/blocks` (sync Conta Conexus), moderação, contestação 90 dias, audit log | Cobertura testes ≥90%, CI com Postgres real |

### Fase 3 — Antispam Avançado (M4) — **3–4 semanas**
| Sprint | Entregáveis | Critérios de Aceite |
|--------|-------------|---------------------|
| 1 | Campanha detector, STIR/SHAKEN, regras 0303, padrão internacional | Detecta campanhas em <50ms |
| 2 | Denúncia identificada (Conta Conexus), anti-abuso avançado, falsos positivos | Falsos positivos <2% em testes |
| 3 | Dataset delta sync (WorkManager), Object Storage/CDN (Linode/Tigris), verificação assinatura | Delta aplica em <2s, rollback funciona |
| 4 | Endpoint orgs verificadas, métricas Prometheus, alertas | SLOs definidos, dashboards Grafana |

### Fase 4 — Produção & Play Store (M5) — **2–3 semanas**
| Sprint | Entregáveis | Critérios de Aceite |
|--------|-------------|---------------------|
| 1 | Segurança: `cargo audit`/`pip-audit`, `trivy`, SAST, pen test básico | Zero critical/high |
| 2 | Acessibilidade: TalkBack, scaling, touch targets, contraste WCAG 2.2 AA | 0 violações axe-core |
| 3 | Performance: startup <2s, DB <50MB, battery <1%/h, memory <150MB | Métricas atendidas |
| 4 | Play Console: listing PT-BR, gráficos, Data Safety, content rating, internal test | Aprovação manual Tiago → production rollout |

---

## 10. Definição de Tarefas para Orquestrador de Agentes

### Agentes Envolvidos

| Agente | Papel | Arquivos/Áreas |
|--------|-------|----------------|
| **A0 (Orquestrador)** | Planejamento, gates, ADRs, conflitos | `SDD.md`, `ADR/*.md`, `ROADMAP.md` |
| **A1 (Android Core)** | CallScreeningService, Room, WorkManager, native bridge | `android/app/src/main/java/.../screening/`, `android/app/src/main/java/.../database/` |
| **A2 (Compose UI)** | Onboarding, Home, History, Settings, Design System | `android/app/src/main/java/.../ui/`, `android/app/src/main/res/` |
| **A3 (Decision Engine)** | Motor de decisão puro (Kotlin multiplatform ou port TS→KT) | `shared/decision-engine/` |
| **A4 (Backend/FastAPI)** | API, Reputation Engine, PostgreSQL, Redis, Workers | `backend/app/`, `backend/workers/`, `backend/alembic/` |
| **A5 (Infra/DevOps)** | Docker, Terraform LKE, GHCR, CI/CD, OpenTelemetry | `infra/`, `.github/workflows/` |
| **A6 (QA/Validator)** | Testes unit/integration/device, performance, segurança, a11y | `tests/`, `android/app/src/androidTest/` |
| **A7 (Release/Docs)** | Install guide, device matrix, release notes, SBOM, signing | `docs/installation/`, `RELEASE_NOTES.md`, `tools/release/` |

### Tarefas Atômicas (Exemplos para Sprint 1–2)

| ID | Agente | Título | Arquivos | Critério | Dependências |
|----|--------|--------|----------|----------|--------------|
| T001 | A1 | Projeto Android base (Kotlin, Compose, Hilt, Room, WorkManager) | `android/build.gradle.kts`, `android/app/build.gradle.kts` | `./gradlew assembleDebug` passa | — |
| T002 | A1 | CallScreeningService + bridge nativo | `android/app/src/main/java/.../screening/*.kt` | Serviço registrado, `isRoleHeld`/`requestRole` funcionam | T001 |
| T003 | A2 | Onboarding 3 passos PT-BR + botão permissão | `android/app/src/main/java/.../ui/onboarding/` | Fluxo completo concede papel | T001 |
| T004 | A3 | Decision Engine Kotlin (port de TS) | `shared/decision-engine/src/commonMain/` | Mesmos testes passam (JUnit) | — |
| T005 | A1 | Room DB: entities (ReputationEntry, HistoryEntry, Blocklist), DAOs, migrations | `android/app/src/main/java/.../database/` | `RoomDatabase` compila, DAOs testados | T001 |
| T006 | A1 | WorkManager: seed init + delta sync periódico | `android/app/src/main/java/.../sync/` | Dataset carrega no 1º run, versão persistida | T005 |
| T007 | A2 | Home screen com simulador de chamada | `android/app/src/main/java/.../ui/home/` | Simula chamada, mostra decisão | T002, T004 |
| T008 | A2 | History screen: lista, detalhes, desbloquear/permitir, denunciar | `android/app/src/main/java/.../ui/history/` | Ações atualizam Room + UI | T005 |
| T009 | A2 | Settings: modos, regras, listas, backup, aparência, conta | `android/app/src/main/java/.../ui/settings/` | Persiste em Room + DataStore | T005 |
| T010 | A6 | Testes unit: decision engine (14+ casos), DB, sync | `shared/decision-engine/src/test/`, `android/app/src/test/` | `./gradlew test` 100% passa | T004, T005 |
| T011 | A6 | Testes instrumentados: CallScreeningService, UI flows | `android/app/src/androidTest/` | Roda em emulador + device real | T002, T007, T008 |

---

## 11. Critérios de Aceite Globais (Definition of Done)

- [ ] **Funcional:** Todos cenários Gherkin (SDD §5) cobertos por testes automatizados
- [ ] **Performance:** Screening p95 < 100ms (local), UI 60fps, startup <2s, mem <150MB
- [ ] **Segurança:** Zero critical/high em SAST/DAST/dependências; chaves produção fora do repo/CI
- [ ] **Privacidade:** Agenda nunca sai do device; k-anônimo + HMAC + PoW validados
- [ ] **Acessibilidade:** WCAG 2.2 AA (TalkBack, scaling 200%, touch targets 48dp, contraste 4.5:1)
- [ ] **Resiliência:** API offline → decisão local; DB corrompido → recria vazio (fail-safe)
- [ ] **Observabilidade:** Traces OpenTelemetry, logs estruturados, métricas Prometheus, alertas SLO
- [ ] **Documentação:** ADRs decisões, install guide por device, runbook ops, SBOM por release
- [ ] **CI/CD:** `check → test → build → security → staging → manual_approval → production`
- [ ] **Assinatura:** AAB assinado AVB v2, chaves dev/prod separadas, rollback protection

---

## 12. ADRs Iniciais (a criar na Fase 1)

| ADR | Título | Status |
|-----|--------|--------|
| ADR-001 | Arquitetura: Android-first, local-first, k-anonymous API | Proposto |
| ADR-002 | Decision Engine: Kotlin multiplatform (shared com backend futuro) | Proposto |
| ADR-003 | Dataset: Ed25519 signed, delta updates, anti-rollback | Proposto |
| ADR-004 | Privacidade: HMAC phone hashes, no contact upload, PoW reports | Proposto |
| ADR-005 | Backend: FastAPI + PostgreSQL + Redis, Celery workers | Proposto |
| ADR-006 | Infra: Linode LKE, GHCR, Terraform, GitHub Actions (Blacksmith) | Proposto |
| ADR-007 | Tenancy: Single-tenant app (sem multi-tenancy no APK) | Proposto |
| ADR-008 | UI Framework: Jetpack Compose + Fluent tokens (Conexus design system) | Proposto |

---

## 13. Próximos Passos Imediatos

1. **A0** cria repositório `conexus-systems/conexus-caller-id` com estrutura inicial
2. **A0** abre ADRs 001–008 para revisão/aceite
3. **A1** inicializa projeto Android (T001)
4. **A3** inicia port do Decision Engine para Kotlin (T004)
5. **A5** prepara `infra/` com Docker Compose local (Postgres, Redis, FastAPI)
6. **A6** define matriz de testes e contrato de evidências

---

## 14. Riscos e Bloqueadores Conhecidos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Permissão `ROLE_CALL_SCREENING` negada por OEMs | Média | Alto | Fallback: Call Directory (iOS) / notificação persistente + manual block |
| Play Console 403 service account (como no antispam-br) | Alta | Bloqueio | Conta de serviço dedicada + verificação prévia; card humano no Trello |
| Enumeração de números via API | Média | Alto | k-anônimo + PoW + rate limit + HMAC; auditoria trimestral |
| Falsos positivos em produção | Média | Alto | Moderação humana, contestação 90 dias, threshold conservador no MVP |
| Device fragmentation (Android 10–15, OEMs) | Alta | Médio | Test matrix: Pixel, Samsung, Xiaomi, Motorola; CI em farm (Firebase Test Lab) |

---

## 15. Métricas de Sucesso (KPIs)

| Métrica | Target MVP | Target Pós-Launch |
|---------|------------|-------------------|
| Taxa de bloqueio correto (precision) | >95% | >98% |
| Falsos positivos (legítimo bloqueado) | <2% | <0.5% |
| Latência screening local (p95) | <100ms | <50ms |
| Taxa de concessão permissão (onboarding) | >60% | >75% |
| Denúncias/dia por usuário ativo | >0.5 | >1.0 |
| Crash-free sessions | >99.5% | >99.9% |
| Play Store rating | ≥4.2 | ≥4.5 |

---

*Este SDD é a fonte de verdade para o orquestrador. Qualquer alteração de escopo, arquitetura ou critério deve passar por ADR e aprovação do A0.*