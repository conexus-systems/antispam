# AdMob — configuração e teste

SDK `react-native-google-mobile-ads` 17.3.1 (linha 17 = RN >= 0.86; o app está em Expo 58 / RN 0.87).

## Onde o anúncio aparece (e onde nunca aparece)

| Lugar | Formato |
|---|---|
| Aba **Histórico** (lista de chamadas) | Banner ancorado no rodapé da lista |
| Aba **Ajustes** | Banner ancorado abaixo do pivô |
| Aba **Verificar**, após uma análise manual concluída | Intersticial: 1 a cada 3 análises, cooldown de 3 min, no máximo 3/hora, nunca na 1ª da sessão |

**Nunca:** tela/fluxo de chamada, `CallScreeningService` nativo, notificações, overlays, onboarding, telas fora do app.
O módulo `src/ads/` não é importado pelo boot (`app/_layout.tsx`) nem por `src/app/screening.ts`: a triagem de chamadas não espera nada do AdMob.

Não há plano pago (doação não desbloqueia nada), então não existe exceção de "assinante sem anúncio".

## Variáveis

| Variável | Efeito |
|---|---|
| `EXPO_PUBLIC_ADMOB_USE_TEST_IDS` | `true` = unidades de exemplo do Google (development/preview). Qualquer outro valor = só unidades do publisher. |
| `EXPO_PUBLIC_ADMOB_ANDROID_APP_ID` / `_IOS_APP_ID` | App ID no manifest (via `app.config.js`). Vazio = App ID de exemplo do Google. |
| `EXPO_PUBLIC_ADMOB_BANNER_ID` | Bloco `antispam_banner`. Vazio = nenhum banner pedido. |
| `EXPO_PUBLIC_ADMOB_INTERSTITIAL_ID` | Bloco `antispam_interstitial`. Vazio = nenhum intersticial pedido. |
| `EXPO_PUBLIC_ADMOB_TEST_DEVICE_IDS` | Opcional, separados por vírgula. |

Sem nenhuma unidade, o SDK nem é inicializado e o formulário de consentimento não aparece.

`app.config.js` falha o build `production` se `USE_TEST_IDS=true`, ou se houver unidade sem `EXPO_PUBLIC_ADMOB_ANDROID_APP_ID`.

## EAS

| Profile | `USE_TEST_IDS` | IDs |
|---|---|---|
| `development` | `true` | exemplo do Google |
| `preview` | `true` | exemplo do Google |
| `production` | `false` | App ID e unidades nas variáveis do EAS (environment `production`) |

## Consentimento (UMP)

`adMobService.initialize()` roda `AdsConsent.gatherConsent()` antes de inicializar o SDK. Se o formulário falhar, usa o consentimento já salvo;
se não houver nenhum, não pede anúncio e tenta de novo na próxima tela. Personalizado só com `OBTAINED`/`NOT_REQUIRED`.
Quando o UMP exige, **Ajustes › geral › Privacidade de anúncios** reabre o formulário.

## Smoke test

1. Não funciona no Expo Go: use `npx expo run:android` ou um APK `preview`.
2. Com `USE_TEST_IDS=true`: Histórico e Ajustes mostram banner de teste; faça 3 análises em Verificar para o intersticial.
3. Para testar as unidades reais, cadastre o aparelho em **AdMob › Configurações › Dispositivos de teste** antes de tocar em anúncios.

## Arquivos

`src/ads/adMob.ts`, `src/ads/config.ts`, `src/ads/frequency.ts`, `src/ui/AdBanner.tsx`, `app.config.js`,
`plugins/withGoogleAndroidLibs.js`, `eas.json`, `__tests__/adMobConsent.test.ts`, `__tests__/adFrequency.test.ts`.
