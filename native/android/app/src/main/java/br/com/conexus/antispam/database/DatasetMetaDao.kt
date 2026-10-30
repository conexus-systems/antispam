package br.com.conexus.antispam.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetMetaDao {
    @Query("SELECT * FROM dataset_meta WHERE id = 1")
    fun observe(): Flow<DatasetMeta?>

    @Query("SELECT * FROM dataset_meta WHERE id = 1")
    suspend fun getSync(): DatasetMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DatasetMeta): Long

    @Query("DELETE FROM dataset_meta")
    suspend fun clear(): Int
}
