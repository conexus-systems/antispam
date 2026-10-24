const { withProjectBuildGradle } = require("@expo/config-plugins");

/**
 * react-native-google-mobile-ads 16.x pulls play-services-ads 25.x, whose classes are
 * compiled with Kotlin 2.3 metadata, but React Native 0.86 ships Kotlin Gradle plugin
 * 2.1.20. Forcing the Kotlin Gradle plugin to the version in expo-build-properties
 * (android.kotlinVersion) keeps the release build compiling.
 */
const KOTLIN_VERSION = "2.3.0";
const MARKER = "// expo-kotlin-gradle-plugin";

function withKotlinGradlePlugin(config) {
  return withProjectBuildGradle(config, (gradleConfig) => {
    const contents = gradleConfig.modResults.contents;
    if (contents.includes(MARKER)) return gradleConfig;

    const match = contents.match(/buildscript\s*\{/);
    if (!match) {
      throw new Error("withKotlinGradlePlugin: could not find buildscript block in android/build.gradle");
    }

    const insertAt = match.index + match[0].length;
    const injection = `
  ${MARKER}
  configurations.classpath.resolutionStrategy.force "org.jetbrains.kotlin:kotlin-gradle-plugin:${KOTLIN_VERSION}", "org.jetbrains.kotlin:kotlin-stdlib:${KOTLIN_VERSION}"`;

    gradleConfig.modResults.contents = contents.slice(0, insertAt) + injection + contents.slice(insertAt);
    return gradleConfig;
  });
}

module.exports = withKotlinGradlePlugin;
