# Riscos e bloqueios

## Bloqueios (impedem avanço)

| # | Bloqueio | Quem resolve | Impacto |
|---|---|---|---|
| B1 | Credencial da Play Developer API ausente | Proprietário | Sem upload, sem consulta de versão |
| B2 | Estado do app na Play Console desconhecido (rascunho ou publicado) | Proprietário / `fleet_status.py` | Define se a 1ª publicação é manual |
| B3 | IDs AdMob de produção não confirmados | Proprietário | Sem monetização real |
| B4 | Política de privacidade com URL pública não confirmada | Proprietário | Obrigatória na Play |
| B5 | Declarações de conteúdo, público e Data safety | Proprietário | Sem envio à loja |
| B6 | Decisão de branch (`feat/metro-w10m-ui` vs `main`) | Proprietário | Versão que vai para a loja |

## Riscos técnicos

| # | Risco | Mitigação |
|---|---|---|
| R1 | Cópias aninhadas do repo confundem build e testes | Exclusões em Jest e tsconfig (feito); decisão sobre o submódulo pendente |
| R2 | `github-release-apk.yml` assina com chave de debug | Não usar esse APK para loja; manter documentado |
| R3 | Workflow citado (`eas-build.yml`) não existe | Criar em Fase 6, com autorização |
| R4 | `npm audit` só informativo | Triar vulnerabilidades antes de torná-lo bloqueante |
| R5 | Permissões sensíveis (`READ_PHONE_STATE`, `CALL_PHONE`) podem atrasar revisão | Justificativa preparada antes do envio |
| R6 | Mock de AsyncStorage pode esconder regressões de persistência | Testes de persistência com storage em memória; revisar na Fase 3 |
| R7 | Monetização ativada cedo demais viola a política de anúncios | Guardas em `app.config.js`; ativação só com pré-requisitos |

## Riscos de processo

- Publicar por engano um build de teste como produção: bloqueado por `app.config.js`, mas depende de o profile correto ser usado.
- Credencial de loja vazar em log: regras em `security.md`.
