/**
 * Configuração do AdMob lida do ambiente de build (EAS `env` / `.env`).
 *
 * - `EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true` (development/preview): unidades de exemplo do Google.
 * - Qualquer outro valor (production): só as unidades do publisher vindas do ambiente.
 *   Unidade vazia = nenhuma requisição daquele formato; nenhuma unidade = SDK nem inicializa.
 */
function env(value: string | undefined): string {
  return (value ?? '').trim();
}

export type AdMobConfig = {
  useTestIds: boolean;
  bannerUnitId: string;
  interstitialUnitId: string;
  testDeviceIds: string[];
};

export function readAdMobConfig(): AdMobConfig {
  return {
    useTestIds: env(process.env.EXPO_PUBLIC_ADMOB_USE_TEST_IDS) === 'true',
    bannerUnitId: env(process.env.EXPO_PUBLIC_ADMOB_BANNER_ID),
    interstitialUnitId: env(process.env.EXPO_PUBLIC_ADMOB_INTERSTITIAL_ID),
    testDeviceIds: env(process.env.EXPO_PUBLIC_ADMOB_TEST_DEVICE_IDS)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
  };
}

export const ADMOB: AdMobConfig = readAdMobConfig();
