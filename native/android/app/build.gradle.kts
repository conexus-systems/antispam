plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "br.com.conexus.antispam"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.conexus.antispam"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    packagingOptions {
        resources.excludes += "META-INF/*.kotlin_module"
    }

    signingConfigs {
        create("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("../keystore/release.keystore")
            storePassword = providers.gradleProperty("keystore.password")
            keyAlias = providers.gradleProperty("keystore.alias")
            keyPassword = providers.gradleProperty("keystore.key.password")
        }
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
    ksp("com.google.dagger:hilt-compiler:$hilt_version")

    // Navigation
    implementation("androidx.navigation:navigation-compose:$navigation_version")
    implementation("androidx.navigation:navigation-fragment-ktx:$navigation_version")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:$datastore_version")
    implementation("androidx.datastore:datastore-core:$datastore_version")
    implementation("androidx.datastore:datastore-preferences-rxjava3:$datastore_version")

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

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // RxJava3 para DataStore
    implementation("io.reactivex.rxjava3:rxandroid:3.1.8")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")

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