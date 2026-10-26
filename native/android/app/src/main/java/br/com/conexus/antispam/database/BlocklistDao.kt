package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist_entries WHERE number = :number")
    suspend fun get(number: String): BlocklistEntry?

    @Query("SELECT * FROM blocklist_entries")
    fun getAll(): Flow<List<BlocklistEntry>>

    @Query("SELECT * FROM blocklist_entries WHERE action = 'block'")
    fun getBlocked(): Flow<List<BlocklistEntry>>

    @Query("SELECT * FROM blocklist_entries WHERE action = 'allow'")
    fun getAllowed(): Flow<List<BlocklistEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BlocklistEntry): Long

    @Query("DELETE FROM blocklist_entries WHERE number = :number")
    suspend fun remove(number: String): Int

    @Query("DELETE FROM blocklist_entries")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM blocklist_entries WHERE action = 'block'")
    suspend fun countBlocked(): Int
}