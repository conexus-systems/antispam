package br.com.conexus.antispam.di

import android.content.Context
import androidx.room.Room
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.database.BlocklistDao
import br.com.conexus.antispam.database.DatasetMetaDao
import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.UserSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideReputationDao(database: AppDatabase): ReputationDao = database.reputationDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideBlocklistDao(database: AppDatabase): BlocklistDao = database.blocklistDao()

    @Provides
    fun provideUserSettingsDao(database: AppDatabase): UserSettingsDao = database.userSettingsDao()

    @Provides
    fun provideRuleDao(database: AppDatabase): RuleDao = database.ruleDao()

    @Provides
    fun provideDatasetMetaDao(database: AppDatabase): DatasetMetaDao = database.datasetMetaDao()
}
