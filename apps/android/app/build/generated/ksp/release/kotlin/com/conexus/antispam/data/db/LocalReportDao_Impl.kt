package com.conexus.antispam.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class LocalReportDao_Impl(
  __db: RoomDatabase,
) : LocalReportDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocalReportEntity: EntityInsertAdapter<LocalReportEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLocalReportEntity = object : EntityInsertAdapter<LocalReportEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `local_reports` (`id`,`e164`,`category`,`at`,`synced`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocalReportEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.e164)
        statement.bindText(3, entity.category)
        statement.bindLong(4, entity.at)
        val _tmp: Int = if (entity.synced) 1 else 0
        statement.bindLong(5, _tmp.toLong())
      }
    }
  }

  public override suspend fun insert(report: LocalReportEntity): Long = performSuspending(__db,
      false, true) { _connection ->
    val _result: Long = __insertAdapterOfLocalReportEntity.insertAndReturnId(_connection, report)
    _result
  }

  public override fun observeLatestPerNumber(): Flow<List<LocalReportEntity>> {
    val _sql: String =
        "SELECT * FROM local_reports WHERE id IN (SELECT MAX(id) FROM local_reports GROUP BY e164)"
    return createFlow(__db, false, arrayOf("local_reports")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfE164: Int = getColumnIndexOrThrow(_stmt, "e164")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfAt: Int = getColumnIndexOrThrow(_stmt, "at")
        val _columnIndexOfSynced: Int = getColumnIndexOrThrow(_stmt, "synced")
        val _result: MutableList<LocalReportEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalReportEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpE164: String
          _tmpE164 = _stmt.getText(_columnIndexOfE164)
          val _tmpCategory: String
          _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          val _tmpAt: Long
          _tmpAt = _stmt.getLong(_columnIndexOfAt)
          val _tmpSynced: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfSynced).toInt()
          _tmpSynced = _tmp != 0
          _item = LocalReportEntity(_tmpId,_tmpE164,_tmpCategory,_tmpAt,_tmpSynced)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
