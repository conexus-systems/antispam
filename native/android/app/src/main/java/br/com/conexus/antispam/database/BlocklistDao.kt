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

    /** action = "block" ou "allow". Mais recentes primeiro. */
    @Query("SELECT * FROM blocklist_entries WHERE action = :action ORDER BY createdAt DESC")
    fun observeByAction(action: String): Flow<List<BlocklistEntry>>

    @Query("SELECT number FROM blocklist_entries WHERE action = :action")
    suspend fun numbers(action: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BlocklistEntry): Long

    @Query("DELETE FROM blocklist_entries WHERE number = :number")
    suspend fun remove(number: String): Int

    @Query("DELETE FROM blocklist_entries")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM blocklist_entries WHERE action = :action")
    suspend fun countByAction(action: String): Int
}
