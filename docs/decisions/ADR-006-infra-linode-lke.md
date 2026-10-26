# ADR-006: Infraestrutura — Linode LKE, GHCR, Terraform, GitHub Actions

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A5

---

## Contexto

Infraestrutura de produção para Conexus Caller ID:

1. Kubernetes gerenciado (não self-managed)
2. Container registry integrado com CI/CD
3. IaC versionado, revisável, reproduzível
4. DNS gerenciado com proxy/WAF
5. Custo previsível, sem vendor lock-in excessivo

---

## Decisão

**Stack Linode-centric:**

| Camada | Escolha | Justificativa |
|--------|---------|---------------|
| **Kubernetes** | Linode LKE (Linode Kubernetes Engine) | Gerenciado, barato, CCM/CSI nativos, bom SLA |
| **Container Registry** | GHCR (GitHub Container Registry) | Integrado no GitHub Actions, gratuito para orgs, OCI compliant |
| **IaC** | Terraform + Linode Provider | Declarativo, state remoto (S3-compat), plan/apply revisado |
| **CI/CD** | GitHub Actions (Blacksmith 2 vCPU) | Nativo no repo, matriz, secrets, environments, manual approval |
| **DNS/CDN/WAF** | Cloudflare (proxied) | DNS rápido, proxy, WAF, cache, DDoS protection, gratuito |
| **Object Storage** | Linode Object Storage (S3-compat) | Datasets, backups, barato, mesmo provedor |
| **Managed DBs** | Linode Managed PostgreSQL + Redis | Backups, HA, patching automático, point-in-time recovery |
| **Secrets** | GitHub Environments + Vault (futuro) | Rotation, audit, least privilege |

---

## Topologia de Rede

```
                         ┌─────────────────┐
                         │   Cloudflare    │
                         │  DNS + Proxy    │
                         │  WAF + Cache    │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │  Linode LKE     │
                         │  (3+ nodes)     │
                         │                 │
                         │  ┌───────────┐  │
                         │  │ Ingress   │  │
                         │  │ NGINX     │  │
                         │  └─────┬─────┘  │
                         │        │        │
                    ┌──────┴──────┴──────┐
                    ▼                    ▼
              ┌──────────┐         ┌──────────┐
              │  API     │         │ Worker   │
              │ (FastAPI)│         │ (Celery) │
              └────┬─────┘         └────┬─────┘
                   │                    │
                   ▼                    ▼
              ┌──────────┐         ┌──────────┐
              │PostgreSQL│         │  Redis   │
              │ (Managed)│         │ (Managed)│
              └──────────┘         └──────────┘
```

---

## Terraform Structure

```
infra/terraform/
├── main.tf              # Providers, LKE, DBs, Object Storage, DNS
├── variables.tf         # Input variables
├── outputs.tf           # Outputs (kubeconfig, connections)
├── versions.tf          # Provider versions
├── modules/
│   ├── lke-cluster/     # LKE cluster + node pools
│   ├── managed-db/      # PostgreSQL + Redis
│   ├── object-storage/  # Bucket + keys
│   └── cloudflare-dns/  # DNS records
└── environments/
    ├── staging/
    │   └── terraform.tfvars
    └── production/
        └── terraform.tfvars
```

---

## GitHub Actions Pipeline

```yaml
# .github/workflows/ci-cd.yml
jobs:
  # 1. Lint + Typecheck (Node/Kotlin/Python)
  # 2. Testes (Unit + Integration + Instrumented)
  # 3. Security (Trivy, pip-audit, TruffleHog)
  # 4. SBOM (CycloneDX)
  # 5. Docker Build + Push (GHCR)
  # 6. Deploy Staging (auto on develop branch)
  # 7. Deploy Production (manual approval on main)
```

**Blacksmith 2 vCPU** — runners GitHub-hosted dedicados, mais rápidos que ubuntu-latest padrão.

**Manual Approval** — `environment: production` + `trstringer/manual-approval` → só Tiago aprova.

---

## Secrets Management

| Secret | Onde | Rotação |
|--------|------|---------|
| `LINODE_TOKEN` | GitHub Env (production) | 90 dias |
| `CLOUDFLARE_API_TOKEN` | GitHub Env (production) | 90 dias |
| `DATABASE_URL` | GitHub Env + Vault | 30 dias |
| `REDIS_URL` | GitHub Env + Vault | 30 dias |
| `HMAC_SECRET` | Vault (never in CI) | 180 dias |
| `ED25519_PRIVATE_KEY` | HSM / Vault (never in CI) | 365 dias |
| `GHCR_TOKEN` | `${{ secrets.GITHUB_TOKEN }}` | Auto |

---

## Disaster Recovery

- **RPO**: 5 min (PostgreSQL PITR + WAL streaming)
- **RTO**: 15 min (LKE cluster recreate + Terraform apply)
- **Backup**: Diário (Linode Managed DB) + Semanal (Object Storage)
- **Runbook**: `docs/runbooks/disaster-recovery.md`

---

## Custos Estimados (USD/mês)

| Recurso | Spec | Custo |
|---------|------|-------|
| LKE Cluster | 3x g6-standard-2 + 2x g6-standard-4 | ~$180 |
| Managed PostgreSQL | g6-standard-2, HA | ~$90 |
| Managed Redis | g6-standard-1, HA | ~$45 |
| Object Storage | 100 GB + transfer | ~$5 |
| Cloudflare | Pro plan | $20 |
| GitHub Actions | Blacksmith (incluído) | $0 |
| **Total** | | **~$340/mês** |

---

## Referências

- [Linode LKE docs](https://www.linode.com/docs/products/compute/kubernetes/)
- [Terraform Linode Provider](https://registry.terraform.io/providers/linode/linode/latest/docs)
- [GHCR docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [SDD §9 Plano de Execução](./SDD.md#9-plano-de-execução-fases)