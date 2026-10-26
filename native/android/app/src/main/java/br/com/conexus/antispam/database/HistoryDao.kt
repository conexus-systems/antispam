package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY at DESC")
    fun getAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries ORDER BY at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntry>): List<Long>

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM history_entries")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM history_entries WHERE action = 'BLOCK'")
    suspend fun countBlocked(): Int

    @Update
    suspend fun update(entry: HistoryEntry): Int
}