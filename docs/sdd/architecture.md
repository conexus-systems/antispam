# Arquitetura — ferramenta central de publicação

## Decisão

Não criar uma plataforma nova. Usar:

- `agents/apps/fleet.yml` como registro de apps (fonte de verdade).
- Scripts versionados na raiz de cada app, em `scripts/publish/`.
- GitHub Actions com `workflow_dispatch` (padrão da frota: nada dispara sozinho).
- EAS Build para gerar o AAB assinado (o profile `production` já existe em `eas.json`).
- Script de upload e status reaproveitado da skill `store-release` (`scripts/upload_release.py`) e `app-fleet/scripts/fleet_status.py`.

## Componentes

```
fleet.yml ──► publish config por app (scripts/publish/config.json)
                 │
                 ├─ validate.sh   package, versionCode, app.json, eas.json, sem IDs de teste em production
                 ├─ build         eas build --profile production --platform android (AAB)
                 ├─ verify        aapt/bundletool: package, versionCode, assinatura esperada
                 ├─ checksum      sha256 do AAB, arquivado como artefato
                 ├─ upload        Google Play Developer API → track interna (com autorização)
                 └─ promote       track interna → fechada/produção (somente com aprovação explícita)
```

## Fluxo em etapas

Cada etapa é independente, tem entrada/saída definidas e falha com mensagem clara.

1. **validate** (local, sem rede): lê `app.json`, `eas.json`, `app.config.js`. Falha se `package` divergir do `fleet.yml`, se `versionCode` não for maior que o último publicado, ou se `production` tiver IDs de teste.
2. **test** (local): `tsc --noEmit` e `jest --ci`.
3. **build** (EAS, remoto mas sem efeito na loja): gera o AAB.
4. **verify** (local): confere package, versionCode e assinatura do AAB contra o esperado.
5. **checksum** (local): gera SHA-256 e registra em `dist-release/`.
6. **upload** (remoto, exige autorização): envia para a track `internal`.
7. **report**: registra o resultado da API (ID da edição, estado).
8. **promote** (remoto, exige aprovação explícita): nunca automático.

## Configuração por app

Um arquivo `scripts/publish/config.json` por app, com:

```json
{
  "slug": "antispam-br",
  "package": "com.conexus.antispam",
  "tech": "expo",
  "eas_profile": "production",
  "track_default": "internal",
  "admob": true
}
```

Os valores devem coincidir com `fleet.yml`. Divergência é erro de validação.

## Local vs. CI

- Local: `validate`, `test`, `verify`, `checksum`. Não exige credencial de loja.
- CI (`workflow_dispatch`): `build` e `upload`. Exige secrets do GitHub (ver `security.md`).

## Limites

- A API pode automatizar tracks, releases e metadados suportados. Ela não elimina declarações de conteúdo, verificação de identidade do desenvolvedor, nem a primeira revisão do Google.
- Não há como validar via API a Data safety e as declarações de conteúdo. Essas continuam manuais, com proposta gerada e confirmação do proprietário.
