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
public class RuleDao_Impl(
  __db: RoomDatabase,
) : RuleDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfRuleEntity: EntityInsertAdapter<RuleEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfRuleEntity = object : EntityInsertAdapter<RuleEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `rules` (`id`,`type`,`pattern`,`action`,`label`,`enabled`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: RuleEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.type)
        statement.bindText(3, entity.pattern)
        statement.bindText(4, entity.action)
        val _tmpLabel: String? = entity.label
        if (_tmpLabel == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpLabel)
        }
        val _tmp: Int = if (entity.enabled) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        statement.bindLong(7, entity.createdAt)
      }
    }
  }

  public override suspend fun upsert(rule: RuleEntity): Long = performSuspending(__db, false, true)
      { _connection ->
    val _result: Long = __insertAdapterOfRuleEntity.insertAndReturnId(_connection, rule)
    _result
  }

  public override fun observeAll(): Flow<List<RuleEntity>> {
    val _sql: String = "SELECT * FROM rules ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("rules")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfPattern: Int = getColumnIndexOrThrow(_stmt, "pattern")
        val _columnIndexOfAction: Int = getColumnIndexOrThrow(_stmt, "action")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfEnabled: Int = getColumnIndexOrThrow(_stmt, "enabled")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<RuleEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: RuleEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpPattern: String
          _tmpPattern = _stmt.getText(_columnIndexOfPattern)
          val _tmpAction: String
          _tmpAction = _stmt.getText(_columnIndexOfAction)
          val _tmpLabel: String?
          if (_stmt.isNull(_columnIndexOfLabel)) {
            _tmpLabel = null
          } else {
            _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          }
          val _tmpEnabled: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfEnabled).toInt()
          _tmpEnabled = _tmp != 0
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          _item =
              RuleEntity(_tmpId,_tmpType,_tmpPattern,_tmpAction,_tmpLabel,_tmpEnabled,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun delete(id: Long) {
    val _sql: String = "DELETE FROM rules WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteExact(pattern: String) {
    val _sql: String = "DELETE FROM rules WHERE type = 'EXACT' AND pattern = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, pattern)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
