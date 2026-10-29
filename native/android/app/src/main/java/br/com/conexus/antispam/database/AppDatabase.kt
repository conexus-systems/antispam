package br.com.conexus.antispam.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReputationEntry::class,
        HistoryEntry::class,
        BlocklistEntry::class,
        UserSettings::class,
        RuleEntity::class,
        DatasetMeta::class,
    ],
    version = 2,
    // exportSchema ligado exigiria o argumento ksp "room.schemaLocation" em app/build.gradle.kts.
    // Ligar junto com a primeira migração real (depois da v1 pública).
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reputationDao(): ReputationDao
    abstract fun historyDao(): HistoryDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun ruleDao(): RuleDao
    abstract fun datasetMetaDao(): DatasetMetaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "conexus_caller_id.db",
                )
                    .addCallback(DefaultsCallback)
                    // O app ainda não foi publicado: não há instalações de produção para preservar.
                    // Migrações reais (Migration(n, n+1)) e exportSchema começam depois da v1 pública
                    // (RNF-07); aí este fallback deve ser removido.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    /**
     * Insere configurações, dataset_meta e regras padrão. onCreate cobre instalação nova;
     * onOpen (INSERT OR IGNORE) cobre o caso de migração destrutiva, em que onCreate não roda.
     */
    private object DefaultsCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DefaultData.insertDefaults(db, System.currentTimeMillis())
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            if (!db.isReadOnly) DefaultData.insertDefaults(db, System.currentTimeMillis())
        }
    }
}
