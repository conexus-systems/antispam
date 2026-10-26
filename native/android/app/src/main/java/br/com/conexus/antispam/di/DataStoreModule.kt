package br.com.conexus.antispam.di

import android.content.Context
import androidx.datastore.preferences.PreferencesKeys
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.rx.RxDataStore
import androidx.datastore.preferences.rx.preferencesDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDataStore(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): RxDataStore {
        return preferencesDataStoreFactory(
            name = "conexus_caller_id_prefs",
            corruptionHandler = { throw it },
            migrations = listOf(
                PreferencesKeys.Migration { mutablePreferences ->
                    // Migrações futuras se necessário
                }
            ),
            produceMigrations = { mutablePreferencesOf() },
            scope = context
        )
    }
}