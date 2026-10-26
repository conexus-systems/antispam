package com.conexus.antispam.`data`.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _ruleDao: Lazy<RuleDao> = lazy {
    RuleDao_Impl(this)
  }

  private val _callEventDao: Lazy<CallEventDao> = lazy {
    CallEventDao_Impl(this)
  }

  private val _localReportDao: Lazy<LocalReportDao> = lazy {
    LocalReportDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "606b0dd857adf0bca58228bb26148b40", "df17cd5aa21431d743b1376a0a9e9339") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `pattern` TEXT NOT NULL, `action` TEXT NOT NULL, `label` TEXT, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rules_type_pattern` ON `rules` (`type`, `pattern`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `call_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `e164` TEXT, `displayNumber` TEXT NOT NULL, `action` TEXT NOT NULL, `score` INTEGER NOT NULL, `stage` TEXT NOT NULL, `category` TEXT, `reasons` TEXT NOT NULL, `at` INTEGER NOT NULL, `elapsedMicros` INTEGER NOT NULL, `feedback` TEXT)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_call_events_at` ON `call_events` (`at`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_call_events_e164` ON `call_events` (`e164`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `local_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `e164` TEXT NOT NULL, `category` TEXT NOT NULL, `at` INTEGER NOT NULL, `synced` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_local_reports_e164` ON `local_reports` (`e164`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '606b0dd857adf0bca58228bb26148b40')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `rules`")
        connection.execSQL("DROP TABLE IF EXISTS `call_events`")
        connection.execSQL("DROP TABLE IF EXISTS `local_reports`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsRules: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRules.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("type", TableInfo.Column("type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("pattern", TableInfo.Column("pattern", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("action", TableInfo.Column("action", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("label", TableInfo.Column("label", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("enabled", TableInfo.Column("enabled", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRules.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRules: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesRules: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesRules.add(TableInfo.Index("index_rules_type_pattern", true, listOf("type",
            "pattern"), listOf("ASC", "ASC")))
        val _infoRules: TableInfo = TableInfo("rules", _columnsRules, _foreignKeysRules,
            _indicesRules)
        val _existingRules: TableInfo = read(connection, "rules")
        if (!_infoRules.equals(_existingRules)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |rules(com.conexus.antispam.data.db.RuleEntity).
              | Expected:
              |""".trimMargin() + _infoRules + """
              |
              | Found:
              |""".trimMargin() + _existingRules)
        }
        val _columnsCallEvents: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCallEvents.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("e164", TableInfo.Column("e164", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("displayNumber", TableInfo.Column("displayNumber", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("action", TableInfo.Column("action", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("score", TableInfo.Column("score", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("stage", TableInfo.Column("stage", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("category", TableInfo.Column("category", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("reasons", TableInfo.Column("reasons", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("at", TableInfo.Column("at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("elapsedMicros", TableInfo.Column("elapsedMicros", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCallEvents.put("feedback", TableInfo.Column("feedback", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCallEvents: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCallEvents: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesCallEvents.add(TableInfo.Index("index_call_events_at", false, listOf("at"),
            listOf("ASC")))
        _indicesCallEvents.add(TableInfo.Index("index_call_events_e164", false, listOf("e164"),
            listOf("ASC")))
        val _infoCallEvents: TableInfo = TableInfo("call_events", _columnsCallEvents,
            _foreignKeysCallEvents, _indicesCallEvents)
        val _existingCallEvents: TableInfo = read(connection, "call_events")
        if (!_infoCallEvents.equals(_existingCallEvents)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |call_events(com.conexus.antispam.data.db.CallEventEntity).
              | Expected:
              |""".trimMargin() + _infoCallEvents + """
              |
              | Found:
              |""".trimMargin() + _existingCallEvents)
        }
        val _columnsLocalReports: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLocalReports.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalReports.put("e164", TableInfo.Column("e164", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalReports.put("category", TableInfo.Column("category", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalReports.put("at", TableInfo.Column("at", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocalReports.put("synced", TableInfo.Column("synced", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLocalReports: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLocalReports: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesLocalReports.add(TableInfo.Index("index_local_reports_e164", false, listOf("e164"),
            listOf("ASC")))
        val _infoLocalReports: TableInfo = TableInfo("local_reports", _columnsLocalReports,
            _foreignKeysLocalReports, _indicesLocalReports)
        val _existingLocalReports: TableInfo = read(connection, "local_reports")
        if (!_infoLocalReports.equals(_existingLocalReports)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |local_reports(com.conexus.antispam.data.db.LocalReportEntity).
              | Expected:
              |""".trimMargin() + _infoLocalReports + """
              |
              | Found:
              |""".trimMargin() + _existingLocalReports)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "rules", "call_events",
        "local_reports")
  }

  public override fun clearAllTables() {
    super.performClear(false, "rules", "call_events", "local_reports")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(RuleDao::class, RuleDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CallEventDao::class, CallEventDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LocalReportDao::class, LocalReportDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun rules(): RuleDao = _ruleDao.value

  public override fun callEvents(): CallEventDao = _callEventDao.value

  public override fun localReports(): LocalReportDao = _localReportDao.value
}
