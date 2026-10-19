const { withProjectBuildGradle } = require('@expo/config-plugins');

/**
 * Fixa a versão do UMP (consentimento) no Gradle para nenhuma dependência transitiva rebaixá-la.
 * Manter em sincronia com `sdkVersions.android.googleUmp` do react-native-google-mobile-ads.
 */
const GOOGLE_ANDROID_LIBS = {
  userMessagingPlatform: '4.0.0',
};

const MARKER = '// expo-google-android-libs';

function withGoogleAndroidLibs(config) {
  return withProjectBuildGradle(config, (gradleConfig) => {
    let contents = gradleConfig.modResults.contents;
    if (contents.includes(MARKER)) return gradleConfig;

    const allProjectsMatch = contents.match(/allprojects\s*\{/);
    if (!allProjectsMatch) {
      throw new Error('withGoogleAndroidLibs: bloco allprojects não encontrado em android/build.gradle');
    }

    const insertAt = allProjectsMatch.index + allProjectsMatch[0].length;
    const injection = `
  ${MARKER}
  configurations.all {
    resolutionStrategy {
      force "com.google.android.ump:user-messaging-platform:${GOOGLE_ANDROID_LIBS.userMessagingPlatform}"
    }
  }`;

    contents = contents.slice(0, insertAt) + injection + contents.slice(insertAt);
    gradleConfig.modResults.contents = contents;
    return gradleConfig;
  });
}

module.exports = withGoogleAndroidLibs;
