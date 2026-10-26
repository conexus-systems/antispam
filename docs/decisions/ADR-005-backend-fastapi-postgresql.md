# ADR-005: Backend — FastAPI + PostgreSQL + Redis + Celery

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A4, A5

---

## Contexto

Backend precisa:

1. API REST assíncrona de alta performance (lookup k-anônimo, denúncias, sync)
2. Processamento assíncrono (moderação, agregação de scores, publicação dataset)
3. Observabilidade nativa (OpenTelemetry, structured logging)
4. Deploy em containers (Linode LKE)
5. Tipo-safe, validado, documentado (OpenAPI)

---

## Decisão

**Stack Python assíncrono:**

| Camada | Tecnologia | Justificativa |
|--------|------------|---------------|
| **API Framework** | FastAPI | Async nativo, Pydantic validation, OpenAPI automático, performance top |
| **Database** | PostgreSQL 16 + asyncpg | ACID, JSONB, índices parciais, extensões (pgvector futuro), maduro |
| **ORM** | SQLAlchemy 2.0 (async) | Type-safe, async, migrações Alembic, flexível |
| **Cache/Queue** | Redis 7 + Celery | Pub/sub, rate limit, Celery broker/result backend, TTL nativo |
| **Workers** | Celery + Redis | Task queue, retry, scheduling, monitoramento Flower |
| **Validação** | Pydantic v2 | Rust-backed, 10-50x mais rápido, JSON Schema |
| **Auth** | API Key header + JWT (futuro Conta Conexus) | Simples, stateless, escalável |
| **Logging** | structlog + JSON | Structured, correlation IDs, OpenTelemetry ready |
| **Migrations** | Alembic | Versionado, autogenerate, rollback seguro |

---

## Arquitetura de Serviços

```
┌─────────────────────────────────────────────────────────────┐
│                    FastAPI App (uvicorn)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │ /lookup  │ │ /reports │ │/reputation│ │ /blocks      │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘   │
│       │            │            │             │             │
│       ▼            ▼            ▼             ▼             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Reputation Engine (services/)            │   │
│  │  - aggregate_scores()    - publish_dataset()         │   │
│  │  - moderate_report()     - detect_campaigns()        │   │
│  └──────────────────────────┬────────────────────────────┘   │
└─────────────────────────────│─────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
   ┌─────────┐           ┌─────────┐           ┌─────────┐
   │PostgreSQL│           │  Redis  │           │ Celery  │
   │ (Async) │           │ (Cache/ │           │ Workers │
   │         │           │  Queue) │           │         │
   └─────────┘           └─────────┘           └─────────┘
```

---

## Workers Celery (Tarefas Assíncronas)

| Task | Trigger | Descrição |
|------|---------|-----------|
| `aggregate_reputation` | Cron (5min) | Recalcula scores agregados a partir de `spam_reports` aprovados |
| `moderate_report_queue` | Event (report created) | Fila de moderação, auto-aprova se critérios atendidos |
| `publish_dataset` | Cron (diário) + Manual | Gera manifest + delta, assina Ed25519, sobe para Object Storage |
| `detect_campaigns` | Cron (hora) | Detecta campanhas coordenadas, atualiza `reputation_scores` |
| `cleanup_expired` | Cron (diário) | Remove nonces PoW expirados, logs antigos, nonces de replay |
| `send_contestation_notice` | Event (contest deadline) | Notifica admin/contato sobre contestação pendente |

---

## Configuração de Produção

```yaml
services:
  api:
    image: ghcr.io/conexus-systems/conexus-caller-id/backend:latest
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2'
          memory: 2G
    environment:
      - ENVIRONMENT=production
      - WORKERS=4
      - DATABASE_URL=postgresql+asyncpg://...
      - REDIS_URL=redis://...
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 3

  worker:
    image: ghcr.io/conexus-systems/conexus-caller-id/backend:latest
    command: celery -A app.workers.celery_app worker --loglevel=info --concurrency=4
    deploy:
      replicas: 2

  beat:
    image: ghcr.io/conexus-systems/conexus-caller-id/backend:latest
    command: celery -A app.workers.celery_app beat --loglevel=info
    deploy:
      replicas: 1
```

---

## Observabilidade

- **Metrics**: Prometheus `/metrics` (FastAPI Instrumentator)
- **Traces**: OpenTelemetry → OTLP → Grafana Tempo
- **Logs**: structlog JSON → Loki
- **Alerts**: Grafana Alertmanager (SLO: latency p99 < 500ms, error rate < 0.1%)

---

## Referências

- [FastAPI docs](https://fastapi.tiangolo.com/)
- [SQLAlchemy 2.0 async](https://docs.sqlalchemy.org/en/20/orm/extensions/asyncio.html)
- [Celery docs](https://docs.celeryq.dev/)
- [SDD §6 API do Backend](./SDD.md#6-api-do-backend)
