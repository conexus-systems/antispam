package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Contagem por ação (somente chamadas reais, sem simuladas). */
data class ActionCount(val action: String, val count: Int)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE action = :action ORDER BY at DESC LIMIT :limit")
    fun observeByAction(action: String, limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    fun observe(id: String): Flow<HistoryEntry?>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun get(id: String): HistoryEntry?

    /** Chamadas reais (não simuladas) mais recentes: alimenta a detecção de campanha. */
    @Query("SELECT * FROM history_entries WHERE simulated = 0 ORDER BY at DESC LIMIT :limit")
    suspend fun getRecentReal(limit: Int): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM history_entries")
    suspend fun clear(): Int

    /** Apaga entradas anteriores ao instante ISO informado (formato fixo, comparação lexicográfica). */
    @Query("DELETE FROM history_entries WHERE at < :beforeIso")
    suspend fun purgeBefore(beforeIso: String): Int

    @Query("UPDATE history_entries SET reported = 1, category = :category WHERE number = :number")
    suspend fun markReportedByNumber(number: String, category: String): Int

    @Query("UPDATE history_entries SET reported = 0, category = NULL WHERE number = :number")
    suspend fun clearReportedByNumber(number: String): Int

    @Query("SELECT action, COUNT(*) AS count FROM history_entries WHERE simulated = 0 GROUP BY action")
    fun observeActionCounts(): Flow<List<ActionCount>>
}
