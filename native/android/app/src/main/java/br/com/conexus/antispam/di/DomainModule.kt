package br.com.conexus.antispam.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import br.com.conexus.antispam.domain.Clock
import br.com.conexus.antispam.domain.LocalPrefsCleaner
import br.com.conexus.antispam.domain.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Dependências da camada domain/. Repositórios e o pipeline são @Singleton @Inject e dispensam binding. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = WallClock

    @Provides
    @Singleton
    fun provideLocalPrefsCleaner(dataStore: DataStore<Preferences>): LocalPrefsCleaner =
        LocalPrefsCleaner { dataStore.edit { it.clear() } }
}
