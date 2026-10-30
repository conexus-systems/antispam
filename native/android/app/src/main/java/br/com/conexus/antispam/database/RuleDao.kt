package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY priority ASC, id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY priority ASC, id ASC")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun get(id: Long): RuleEntity?

    /** Devolve o id gerado (ou o id informado). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean): Int

    @Query("UPDATE rules SET action = :action WHERE id = :id")
    suspend fun setAction(id: Long, action: String): Int

    @Query("UPDATE rules SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int): Int

    @Query("DELETE FROM rules")
    suspend fun clear(): Int
}
