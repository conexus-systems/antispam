import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// AdMob: IDs só por configuração (-P ou variável de ambiente). Padrão = IDs de TESTE do Google.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerId = "ca-app-pub-3940256099942544/6300978111"
val admobTestInterstitialId = "ca-app-pub-3940256099942544/1033173712"

fun admobConfig(name: String, testDefault: String): String =
    (providers.gradleProperty(name).orNull ?: System.getenv(name))?.trim()?.takeIf { it.isNotEmpty() } ?: testDefault

val admobAppId = admobConfig("ADMOB_ANDROID_APP_ID", admobTestAppId)
val admobBannerId = admobConfig("ADMOB_BANNER_ID", admobTestBannerId)
val admobInterstitialId = admobConfig("ADMOB_INTERSTITIAL_ID", admobTestInterstitialId)

android {
    namespace = "br.com.conexus.antispam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.conexus.antispam"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "0.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Chave de upload da Play: lida de ~/secrets (fora do repositório). Sem ela, release sai sem assinatura.
    val uploadProps = Properties().apply {
        val f = File(System.getProperty("user.home"), "secrets/antispam-br-upload.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

    signingConfigs {
        if (uploadProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(uploadProps.getProperty("storeFile"))
                storePassword = uploadProps.getProperty("storePassword")
                keyAlias = uploadProps.getProperty("keyAlias")
                keyPassword = uploadProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug sempre usa os IDs de teste.
            manifestPlaceholders["ADMOB_APP_ID"] = admobTestAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobTestInterstitialId\"")
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
            // R8 desligado na primeira versão: Room/Hilt/serialization ainda sem regras validadas em release.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packagingOptions {
        resources.excludes += "META-INF/*.kotlin_module"
    }
}

dependencies {
    // Core Android
    val activity_version = "1.9.0"
    val core_ktx_version = "1.13.1"
    val lifecycle_version = "2.7.0"
    val room_version = "2.6.1"
    val work_version = "2.9.0"
    val hilt_version = "2.51"
    val navigation_version = "2.7.6"
    val datastore_version = "1.1.1"

    // Compose BOM
    val compose_bom = platform("androidx.compose:compose-bom:2024.05.00")

    implementation(compose_bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:$activity_version")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version")

    // Core
    implementation("androidx.core:core-ktx:$core_ktx_version")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("androidx.room:room-paging:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:$work_version")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:$hilt_version")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:$hilt_version")

    // Navigation
    implementation("androidx.navigation:navigation-compose:$navigation_version")
    implementation("androidx.navigation:navigation-fragment-ktx:$navigation_version")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:$datastore_version")

    // Retrofit / OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // Crypto (Ed25519, HMAC)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // AdMob + consentimento UMP (play-services-ads 23.6.0 é a última compatível com compileSdk 34)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // RxJava3 para DataStore

    // Testes
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("androidx.room:room-testing:$room_version")
    testImplementation("org.robolectric:robolectric:4.11")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.7")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.6.7")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hilt_version")
    kspAndroidTest("com.google.dagger:hilt-compiler:$hilt_version")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=androidx.lifecycle.ExperimentalLifecycleApi"
        )
    }
}

// Guarda de release: não gera AAB/APK de release com IDs de teste do AdMob (como assertStoreSafe do app.config.js).
gradle.taskGraph.whenReady {
    val releaseBuild = allTasks.any { t ->
        t.project == project && t.name.matches(Regex("(bundle|assemble)(.*)Release"))
    }
    if (releaseBuild) {
        val testValues = setOf(admobTestAppId, admobTestBannerId, admobTestInterstitialId)
        val usingTest = listOf(admobAppId, admobBannerId, admobInterstitialId).filter { it in testValues }
        if (usingTest.isNotEmpty()) {
            throw GradleException(
                "AdMob: build de release com ID(s) de TESTE do Google (" + usingTest.joinToString() + "). " +
                    "Informe os IDs reais com -PADMOB_ANDROID_APP_ID, -PADMOB_BANNER_ID e -PADMOB_INTERSTITIAL_ID " +
                    "(ou variaveis de ambiente de mesmo nome). Veja docs/sdd/admob-kotlin.md."
            )
        }
    }
}
