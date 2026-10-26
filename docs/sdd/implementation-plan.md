# Plano de implementação

Trabalho em lotes pequenos. Cada lote termina com evidência.

## Fase 2 — base verificável (concluída nesta sessão)

| Tarefa | Arquivos | Status |
|---|---|---|
| Isolar Jest das cópias aninhadas e worktrees | `package.json` (`modulePathIgnorePatterns`) | Feito |
| Excluir cópias do typecheck | `tsconfig.json` (`exclude`) | Feito |
| Corrigir mock do AsyncStorage | `jest.setup.js` | Feito |
| Registrar estado em `docs/sdd/` | `docs/sdd/*.md` | Feito (rascunho) |

Mudanças ainda **não commitadas**, na branch `feat/metro-w10m-ui`.

## Fase 3 — build reproduzível (próximo lote, sem remoto)

1. Rodar `expo export --platform android` e registrar saída.
2. Criar `scripts/publish/validate.sh` (package, versionCode, IDs de teste em production).
3. Criar `scripts/publish/checksum.sh` (SHA-256 do AAB).
4. Rodar `eas build --profile production --platform android --local` ou em nuvem **somente com autorização**, pois consome créditos EAS e gera artefato.

## Fase 4 — AdMob e consentimento

1. Validar IDs de teste em build `preview` (dispositivo de teste).
2. Validar cenários UMP: consentimento obtido, negado, não exigido.
3. Confirmar com o proprietário: existência do app e unidades na conta AdMob.

## Fase 5 — loja

1. Gerar proposta de Data safety a partir de `docs/PRIVACY.md` e das permissões. Não enviar.
2. Checklist de metadados e recursos gráficos.
3. Confirmar URL pública da política de privacidade.

## Fase 6 — CI e envio (exige autorização)

1. Criar `.github/workflows/publish-android.yml` com `workflow_dispatch`, inputs de track e confirmação.
2. Configurar secrets (ver `security.md`).
3. Upload para track interna **somente após autorização explícita**.
4. Produção: fora deste plano até aprovação por escrito.

## Pendências de decisão do proprietário

- Branch do piloto: manter `feat/metro-w10m-ui` ou integrar em `main` antes da publicação.
- Remover ou manter o submódulo aninhado `antispam-br/antispam-br`.
- Remover `_wt-antispam-admob` e `.claude/worktrees` do disco (não foi feito: são cópias de trabalho).
