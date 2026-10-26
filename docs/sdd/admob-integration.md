# Integração AdMob

Estado atual (código): já implementada em `src/ads/` e `app.config.js`. Este documento descreve o que é exigido antes de ativar monetização real.

## Ambientes

| Profile EAS | `EXPO_PUBLIC_ADMOB_USE_TEST_IDS` | IDs | Pode ir para a loja? |
|---|---|---|---|
| `development` | `true` | exemplo do Google | não |
| `preview` | `true` | exemplo do Google | não |
| `production` | `false` | App ID e unidades reais | sim, após validação |

Guardas existentes:
- `app.config.js` falha o build `production` se `USE_TEST_IDS=true`.
- `app.config.js` falha se houver unidade sem `EXPO_PUBLIC_ADMOB_ANDROID_APP_ID`.

## Pré-requisitos antes de ativar IDs reais

1. Existe um app Android correspondente na conta AdMob com `com.conexus.antispam`. **Não verificado.**
2. O App ID do AdMob está em `EXPO_PUBLIC_ADMOB_ANDROID_APP_ID` no ambiente `production` do EAS. **Não verificado.**
3. As unidades `antispam_banner` e `antispam_interstitial` existem na conta. **Não verificado.**
4. Teste feito com IDs de teste em `preview`, com o dispositivo de teste configurado em `EXPO_PUBLIC_ADMOB_TEST_DEVICE_IDS`.
5. Nunca clicar em anúncio real para validar a integração.

## Consentimento (UMP)

- Implementado em `src/ads/adMob.ts`: `gatherConsent` antes da primeira requisição, `applyConsentInfo` aplica o estado, `showPrivacyOptionsForm` expõe o formulário de opções de privacidade quando exigido.
- Sem consentimento possível, nenhuma requisição de anúncio é feita.
- Pendente de validação: os cenários de consentimento obtido, negado e não exigido (região fora da EEA/UK), em build `preview`. **Não executado nesta fase.**

## Formatos

| Formato | Onde | Regra |
|---|---|---|
| Banner | Histórico (rodapé da lista) e Ajustes | Nunca na tela de chamada |
| Intersticial | Verificar, após análise manual | 1 a cada 3, cooldown 3 min, máximo 3/hora, nunca na 1ª da sessão |

Não há anúncio em: fluxo de chamada, `CallScreeningService`, notificações, overlays, onboarding.

## Público-alvo

- O app é de consumo geral, sem foco infantil. A classificação de público e a declaração de anúncios na Play Console **dependem do proprietário** e não foram marcadas.
- Personalização de anúncios só com consentimento via UMP.

## Ativação

Não ativar monetização real até que os pré-requisitos 1 a 4 estejam marcados com evidência.
