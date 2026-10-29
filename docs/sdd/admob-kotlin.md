# AdMob no app Kotlin (native/android)

Banner adaptativo + intersticial, com consentimento UMP. Código em `br.com.conexus.antispam.ads`.
Nada de anúncios em `screening/`, `sync/` ou `decision/`: a triagem funciona sem o SDK carregado.

## IDs reais (nunca no código)

Fonte: propriedade Gradle ou variável de ambiente de mesmo nome. Padrão = IDs de teste do Google.

```bash
./gradlew :app:bundleRelease \
  -PADMOB_ANDROID_APP_ID=ca-app-pub-XXXX~YYYY \
  -PADMOB_BANNER_ID=ca-app-pub-XXXX/AAAA \
  -PADMOB_INTERSTITIAL_ID=ca-app-pub-XXXX/BBBB
```

- App ID -> `manifestPlaceholders["ADMOB_APP_ID"]`; banner/intersticial -> `BuildConfig.ADMOB_BANNER_ID` / `ADMOB_INTERSTITIAL_ID`.
- Debug sempre usa teste. `bundleRelease`/`assembleRelease` com qualquer ID de teste FALHA (guarda no `app/build.gradle.kts`).

## Uso (pela UI)

- `AdsManager.start(activity)` uma vez por abertura (MainActivity.onCreate): pede UMP, só então inicializa o SDK.
- `AdBanner(adsManager)` no rodapé de telas de lista/histórico/ajustes (some sem consentimento ou se falhar).
- `InterstitialController.onVerificationCompleted(activity)` ao fim de cada verificação manual (1 a cada 3, intervalo mínimo 3 min, máx. 3/h; nunca em chamada). `preload()` ao abrir a aba Verificar.
- Ajustes: se `adsManager.state.value.privacyOptionsRequired`, mostrar "Opções de privacidade" -> `adsManager.showPrivacyOptions(activity)`.

## Play Console

- Conteúdo do app > Anúncios: **Sim, o app contém anúncios**.
- ID de publicidade: **Sim** (permissão `AD_ID` declarada no manifesto); finalidade: Publicidade ou marketing.
- Data Safety: declarar coleta de "Identificadores de dispositivo ou outros" e "Dados de uso/diagnóstico" compartilhados com o AdMob (Google) para publicidade/analytics.
- Público-alvo: não infantil (não marcar o app como voltado a crianças).
