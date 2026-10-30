package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReputationDao {
    @Query("SELECT * FROM reputation_entries WHERE key = :canonical")
    suspend fun get(canonical: String): ReputationEntry?

    @Query("SELECT * FROM reputation_entries")
    fun getAll(): Flow<List<ReputationEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReputationEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ReputationEntry>): List<Long>

    @Query("DELETE FROM reputation_entries WHERE key = :canonical")
    suspend fun remove(canonical: String): Int

    @Query("DELETE FROM reputation_entries")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM reputation_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM reputation_entries WHERE score >= :minScore ORDER BY score DESC LIMIT :limit")
    suspend fun getHighScore(minScore: Int, limit: Int): List<ReputationEntry>
}