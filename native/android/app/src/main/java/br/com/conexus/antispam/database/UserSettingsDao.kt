package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun get(): Flow<UserSettings>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSync(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettings): Long

    @Update
    suspend fun update(settings: UserSettings): Int
}