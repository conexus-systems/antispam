// native/android/build.gradle.kts
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.13" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}