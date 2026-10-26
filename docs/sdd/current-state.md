# Estado atual — antispam-br (verificado em 2026-10-22)

Todos os dados abaixo foram lidos do repositório ou de comandos executados localmente. Nada foi verificado na Play Console nem no AdMob.

## Identificação

| Campo | Valor | Fonte |
|---|---|---|
| Nome | AntiSpam BR | `app.json` |
| Tecnologia | Expo SDK ~58, React Native 0.87.1, React 19.3, TypeScript | `package.json` |
| `applicationId` | `com.conexus.antispam` | `app.json` (`android.package`) e `agents/apps/fleet.yml` |
| Versão | `0.4.0` | `package.json`, `app.json` |
| `versionCode` | `5` | `app.json` (`android.versionCode`) |
| `versionCode` gerenciado por | `eas.json` com `appVersionSource: local` | `eas.json` |
| Branch atual | `feat/metro-w10m-ui` (não é `main`) | `git branch` |
| Repositório | `antispam-br` com submódulo aninhado `antispam-br/antispam-br` | `git ls-tree` / `.git` |

## Build e testes

| Verificação | Resultado |
|---|---|
| `npx tsc --noEmit` | **Passa** (exit 0), após limpeza de configuração em `tsconfig.json` |
| `npx jest --ci` | **Passa**: 10 suítes, 116 testes |
| `npx expo export --platform android` | Não executado nesta fase |
| `eas build --profile production` | Não executado nesta fase |

Antes da limpeza, o estado era: `tsc` com 189 erros (cópias aninhadas e worktrees entravam na compilação) e Jest com 12 suítes com falha (conflito de Haste map entre cópias do pacote `@conexus/account-expo` e mock de AsyncStorage não aplicado).

## Assinatura

- Nenhum keystore, `google-services.json` ou service account versionado (`git ls-files` verificado).
- `.gitignore` cobre `*.jks`, `*.p8`, `*.p12`, `*.key`, `.env*.local`, `.env`.
- Não foi verificado se a Play App Signing está ativa para este app.

## AdMob

- SDK `react-native-google-mobile-ads` 17.3.1 no `package.json`.
- Configuração por ambiente em `app.config.js` e `src/ads/config.ts`:
  - `EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true` em `development` e `preview`.
  - `production` falha o build se houver IDs de teste, ou unidades sem App ID.
- Consentimento UMP implementado em `src/ads/adMob.ts` (`gatherConsent`, `applyConsentInfo`, `showPrivacyOptionsForm`).
- Formatos: banner (Histórico e Ajustes) e intersticial (Verificar, com frequência limitada). Detalhes em `docs/ADMOB-TESTING.md`.
- Não verificado: se os IDs de produção existem na conta AdMob e se estão configurados no ambiente `production` do EAS.

## Privacidade e loja

- `docs/PRIVACY.md` existe (documento interno). Não foi verificado se há uma URL pública acessível.
- Data safety e classificação indicativa: não verificadas.
- Ícone adaptativo presente em `assets/` (`android-icon-*.png`, `icon.png`).

## CI

- `.github/workflows/ci.yml`: `workflow_dispatch` apenas. Roda typecheck, jest, `expo export` e auditoria.
- `.github/workflows/github-release-apk.yml`: gera APK assinado com a chave de **debug** (fora da Play).
- O comentário de `github-release-apk.yml` cita um `eas-build.yml`, que **não existe** neste repositório. Não há workflow de AAB de produção versionado.
