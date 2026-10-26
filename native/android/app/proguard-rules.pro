# ProGuard rules for AntiSpam BR Native Android Module

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.HiltApplication { *; }

# Keep Room entities and DAOs
-keep class br.com.conexus.antispam.database.** { *; }

# Keep Decision Engine (KMP shared)
-keep class br.com.conexus.antispam.decision.** { *; }

# Keep screening service and bridge
-keep class br.com.conexus.antispam.screening.** { *; }

# Keep sync workers
-keep class br.com.conexus.antispam.sync.** { *; }

# Keep React Native TurboModule
-keep class br.com.conexus.antispam.screening.AntiSpamScreeningModule { *; }
-keep class br.com.conexus.antispam.screening.ScreeningCallbackImpl { *; }

# Keep Hilt EntryPoints
-keep class * implements dagger.hilt.EntryPoint { *; }

# Keep SQLCipher
-keep class net.sqlcipher.** { *; }

# Keep BouncyCastle crypto
-keep class org.bouncycastle.** { *; }

# Keep OkHttp/Retrofit
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Accompanist
-keep class com.google.accompanist.** { *; }

# Keep Coil
-keep class io.coil_kt.** { *; }

# Keep RxJava3
-keep class io.reactivex.rxjava3.** { *; }

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }

# Keep Hilt/Room generated
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.Dao { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Don't warn about missing classes
-dontwarn org.bouncycastle.**
-dontwarn net.sqlcipher.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**
-dontwarn com.google.accompanist.**
-dontwarn io.coil_kt.**
-dontwarn io.reactivex.rxjava3.**