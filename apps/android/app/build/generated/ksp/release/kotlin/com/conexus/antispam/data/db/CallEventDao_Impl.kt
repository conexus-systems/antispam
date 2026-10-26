package com.conexus.antispam.`data`.db

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
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
public class CallEventDao_Impl(
  __db: RoomDatabase,
) : CallEventDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCallEventEntity: EntityInsertAdapter<CallEventEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCallEventEntity = object : EntityInsertAdapter<CallEventEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `call_events` (`id`,`e164`,`displayNumber`,`action`,`score`,`stage`,`category`,`reasons`,`at`,`elapsedMicros`,`feedback`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CallEventEntity) {
        statement.bindLong(1, entity.id)
        val _tmpE164: String? = entity.e164
        if (_tmpE164 == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpE164)
        }
        statement.bindText(3, entity.displayNumber)
        statement.bindText(4, entity.action)
        statement.bindLong(5, entity.score.toLong())
        statement.bindText(6, entity.stage)
        val _tmpCategory: String? = entity.category
        if (_tmpCategory == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCategory)
        }
        statement.bindText(8, entity.reasons)
        statement.bindLong(9, entity.at)
        statement.bindLong(10, entity.elapsedMicros)
        val _tmpFeedback: String? = entity.feedback
        if (_tmpFeedback == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpFeedback)
        }
      }
    }
  }

  public override suspend fun insert(event: CallEventEntity): Long = performSuspending(__db, false,
      true) { _connection ->
    val _result: Long = __insertAdapterOfCallEventEntity.insertAndReturnId(_connection, event)
    _result
  }

  public override fun observeRecent(limit: Int): Flow<List<CallEventEntity>> {
    val _sql: String = "SELECT * FROM call_events ORDER BY at DESC LIMIT ?"
    return createFlow(__db, false, arrayOf("call_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfE164: Int = getColumnIndexOrThrow(_stmt, "e164")
        val _columnIndexOfDisplayNumber: Int = getColumnIndexOrThrow(_stmt, "displayNumber")
        val _columnIndexOfAction: Int = getColumnIndexOrThrow(_stmt, "action")
        val _columnIndexOfScore: Int = getColumnIndexOrThrow(_stmt, "score")
        val _columnIndexOfStage: Int = getColumnIndexOrThrow(_stmt, "stage")
        val _columnIndexOfCategory: Int = getColumnIndexOrThrow(_stmt, "category")
        val _columnIndexOfReasons: Int = getColumnIndexOrThrow(_stmt, "reasons")
        val _columnIndexOfAt: Int = getColumnIndexOrThrow(_stmt, "at")
        val _columnIndexOfElapsedMicros: Int = getColumnIndexOrThrow(_stmt, "elapsedMicros")
        val _columnIndexOfFeedback: Int = getColumnIndexOrThrow(_stmt, "feedback")
        val _result: MutableList<CallEventEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CallEventEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpE164: String?
          if (_stmt.isNull(_columnIndexOfE164)) {
            _tmpE164 = null
          } else {
            _tmpE164 = _stmt.getText(_columnIndexOfE164)
          }
          val _tmpDisplayNumber: String
          _tmpDisplayNumber = _stmt.getText(_columnIndexOfDisplayNumber)
          val _tmpAction: String
          _tmpAction = _stmt.getText(_columnIndexOfAction)
          val _tmpScore: Int
          _tmpScore = _stmt.getLong(_columnIndexOfScore).toInt()
          val _tmpStage: String
          _tmpStage = _stmt.getText(_columnIndexOfStage)
          val _tmpCategory: String?
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _tmpCategory = null
          } else {
            _tmpCategory = _stmt.getText(_columnIndexOfCategory)
          }
          val _tmpReasons: String
          _tmpReasons = _stmt.getText(_columnIndexOfReasons)
          val _tmpAt: Long
          _tmpAt = _stmt.getLong(_columnIndexOfAt)
          val _tmpElapsedMicros: Long
          _tmpElapsedMicros = _stmt.getLong(_columnIndexOfElapsedMicros)
          val _tmpFeedback: String?
          if (_stmt.isNull(_columnIndexOfFeedback)) {
            _tmpFeedback = null
          } else {
            _tmpFeedback = _stmt.getText(_columnIndexOfFeedback)
          }
          _item =
              CallEventEntity(_tmpId,_tmpE164,_tmpDisplayNumber,_tmpAction,_tmpScore,_tmpStage,_tmpCategory,_tmpReasons,_tmpAt,_tmpElapsedMicros,_tmpFeedback)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeStoppedSince(since: Long): Flow<Int> {
    val _sql: String =
        "SELECT COUNT(*) FROM call_events WHERE action IN ('BLOCK','SILENCE') AND at >= ?"
    return createFlow(__db, false, arrayOf("call_events")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, since)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun setFeedback(id: Long, feedback: String) {
    val _sql: String = "UPDATE call_events SET feedback = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, feedback)
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun prune(before: Long): Int {
    val _sql: String = "DELETE FROM call_events WHERE at < ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, before)
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
