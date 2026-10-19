/**
 * Config dinâmica sobre o app.json: só injeta o AdMob, cujo App ID vem do ambiente de build.
 *
 * EXPO_PUBLIC_ADMOB_ANDROID_APP_ID / _IOS_APP_ID ausentes → App ID de EXEMPLO do Google (o SDK exige
 * um valor no manifest). Sem unidades configuradas o app não faz nenhuma requisição de anúncio.
 */
const GOOGLE_SAMPLE_APP_ID = {
  android: 'ca-app-pub-3940256099942544~3347511713',
  ios: 'ca-app-pub-3940256099942544~1458002511',
};

function env(name) {
  return (process.env[name] || '').trim();
}

function assertStoreSafe() {
  if (process.env.EAS_BUILD_PROFILE !== 'production') return;
  if (env('EXPO_PUBLIC_ADMOB_USE_TEST_IDS') === 'true') {
    throw new Error('AdMob: EXPO_PUBLIC_ADMOB_USE_TEST_IDS=true não é permitido no profile production.');
  }
  const hasUnit = env('EXPO_PUBLIC_ADMOB_BANNER_ID') || env('EXPO_PUBLIC_ADMOB_INTERSTITIAL_ID');
  if (hasUnit && !env('EXPO_PUBLIC_ADMOB_ANDROID_APP_ID')) {
    throw new Error('AdMob: unidades de produção sem EXPO_PUBLIC_ADMOB_ANDROID_APP_ID (o app usaria o App ID de exemplo).');
  }
}

module.exports = ({ config }) => {
  assertStoreSafe();
  return {
    ...config,
    plugins: [
      ...(config.plugins || []),
      [
        'react-native-google-mobile-ads',
        {
          androidAppId: env('EXPO_PUBLIC_ADMOB_ANDROID_APP_ID') || GOOGLE_SAMPLE_APP_ID.android,
          iosAppId: env('EXPO_PUBLIC_ADMOB_IOS_APP_ID') || GOOGLE_SAMPLE_APP_ID.ios,
        },
      ],
      './plugins/withGoogleAndroidLibs',
    ],
  };
};
