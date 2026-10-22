# SDD — Visão geral: publicação Android (Google Play + AdMob)

**Status:** rascunho para revisão · **Piloto:** `antispam-br` · **Data:** 2026-10-22

## Objetivo

Criar um pipeline reutilizável para preparar, validar, assinar, enviar e (com autorização) publicar apps Android da frota CONEXUS na Google Play, com monetização AdMob quando aplicável.

## Escopo desta fase

- Piloto: `antispam-br` (Expo / React Native, `com.conexus.antispam`).
- Infra reaproveitada: `agents/apps/fleet.yml` e as skills `app-fleet`, `store-release`, `play-console`, `play-console-app-setup`.
- Fora de escopo agora: os demais apps, publicação em produção, alteração de recursos remotos (Play Console, AdMob).

## Princípios

1. **Scripts e workflows versionados, idempotentes e testáveis.** Nada de plataforma nova se script + GitHub Actions bastarem.
2. **Separação de ambientes.** `development`/`preview` usam IDs de teste do Google; `production` exige IDs reais e falha se houver IDs de teste.
3. **Nenhuma ação remota sem autorização explícita.** Upload para track interna e produção exigem confirmação do proprietário.
4. **Não gerar nem substituir chaves de assinatura.** Play App Signing é preservado.
5. **Estados separados.** Build local ≠ upload ≠ release criada ≠ em revisão ≠ aprovado ≠ publicado.

## Documentos desta pasta

| Arquivo | Conteúdo |
|---|---|
| `current-state.md` | Inventário e estado verificado |
| `architecture.md` | Desenho da ferramenta central |
| `security.md` | Credenciais, segredos, privilégio mínimo |
| `admob-integration.md` | Monetização, consentimento e ambientes |
| `google-play-release.md` | Trilhas, versionCode, metadados, revisão |
| `acceptance-criteria.md` | Critérios verificáveis por etapa |
| `implementation-plan.md` | Tarefas em lotes |
| `risks-and-blockers.md` | Riscos e pendências |
