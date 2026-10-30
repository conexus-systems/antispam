package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.ActionCount
import br.com.conexus.antispam.database.BlocklistDao
import br.com.conexus.antispam.database.BlocklistEntry
import br.com.conexus.antispam.database.DatasetMeta
import br.com.conexus.antispam.database.DatasetMetaDao
import br.com.conexus.antispam.database.DefaultData
import br.com.conexus.antispam.database.HistoryDao
import br.com.conexus.antispam.database.HistoryEntry
import br.com.conexus.antispam.database.ReputationDao
import br.com.conexus.antispam.database.ReputationEntry
import br.com.conexus.antispam.database.RuleDao
import br.com.conexus.antispam.database.RuleEntity
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.database.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** DAOs em memória: os DAOs do Room são interfaces, então os testes rodam na JVM sem Android. */

class FakeSettingsDao : UserSettingsDao {
    val state = MutableStateFlow<UserSettings?>(null)
    override fun observe(): Flow<UserSettings?> = state
    override suspend fun getSync(): UserSettings? = state.value
    override suspend fun upsert(settings: UserSettings): Long { state.value = settings; return 1 }
    override suspend fun clear(): Int { state.value = null; return 1 }
}

class FakeBlocklistDao : BlocklistDao {
    val state = MutableStateFlow<Map<String, BlocklistEntry>>(emptyMap())
    override suspend fun get(number: String) = state.value[number]
    override fun observeByAction(action: String): Flow<List<BlocklistEntry>> =
        state.map { m -> m.values.filter { it.action == action }.sortedByDescending { it.createdAt } }
    override suspend fun numbers(action: String) = state.value.values.filter { it.action == action }.map { it.number }
    override suspend fun upsert(entry: BlocklistEntry): Long { state.value = state.value + (entry.number to entry); return 1 }
    override suspend fun remove(number: String): Int { val had = number in state.value; state.value = state.value - number; return if (had) 1 else 0 }
    override suspend fun clear(): Int { state.value = emptyMap(); return 1 }
    override suspend fun countByAction(action: String) = state.value.values.count { it.action == action }
}

class FakeRuleDao : RuleDao {
    val state = MutableStateFlow<List<RuleEntity>>(emptyList())
    private var nextId = 100L
    private fun sorted(l: List<RuleEntity>) = l.sortedWith(compareBy({ it.priority }, { it.id }))
    override fun observeAll(): Flow<List<RuleEntity>> = state.map(::sorted)
    override suspend fun getAll() = sorted(state.value)
    override suspend fun get(id: Long) = state.value.firstOrNull { it.id == id }
    override suspend fun upsert(rule: RuleEntity): Long {
        val r = if (rule.id == 0L) rule.copy(id = nextId++) else rule
        state.value = state.value.filter { it.id != r.id } + r
        return r.id
    }
    override suspend fun delete(id: Long): Int { val n = state.value.size; state.value = state.value.filter { it.id != id }; return n - state.value.size }
    private fun mutate(id: Long, f: (RuleEntity) -> RuleEntity): Int {
        var n = 0
        state.value = state.value.map { if (it.id == id) { n++; f(it) } else it }
        return n
    }
    override suspend fun setEnabled(id: Long, enabled: Boolean) = mutate(id) { it.copy(enabled = enabled) }
    override suspend fun setAction(id: Long, action: String) = mutate(id) { it.copy(action = action) }
    override suspend fun setPriority(id: Long, priority: Int) = mutate(id) { it.copy(priority = priority) }
    override suspend fun clear(): Int { state.value = emptyList(); return 1 }
}

class FakeReputationDao : ReputationDao {
    val state = MutableStateFlow<Map<String, ReputationEntry>>(emptyMap())
    override suspend fun get(canonical: String) = state.value[canonical]
    override fun getAll(): Flow<List<ReputationEntry>> = state.map { it.values.toList() }
    override suspend fun upsert(entry: ReputationEntry): Long { state.value = state.value + (entry.key to entry); return 1 }
    override suspend fun upsertAll(entries: List<ReputationEntry>): List<Long> { entries.forEach { upsert(it) }; return entries.map { 1L } }
    override suspend fun remove(canonical: String): Int { state.value = state.value - canonical; return 1 }
    override suspend fun clear(): Int { state.value = emptyMap(); return 1 }
    override suspend fun count() = state.value.size
    override suspend fun getHighScore(minScore: Int, limit: Int) =
        state.value.values.filter { it.score >= minScore }.sortedByDescending { it.score }.take(limit)
}

class FakeHistoryDao : HistoryDao {
    val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private fun byAt() = state.value.sortedByDescending { it.at }
    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> = state.map { byAt().take(limit) }
    override fun observeByAction(action: String, limit: Int): Flow<List<HistoryEntry>> =
        state.map { byAt().filter { it.action == action }.take(limit) }
    override fun observe(id: String): Flow<HistoryEntry?> = state.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun get(id: String) = state.value.firstOrNull { it.id == id }
    override suspend fun getRecentReal(limit: Int) = byAt().filter { !it.simulated }.take(limit)
    override suspend fun insert(entry: HistoryEntry): Long { state.value = state.value.filter { it.id != entry.id } + entry; return 1 }
    override suspend fun delete(id: String): Int { val n = state.value.size; state.value = state.value.filter { it.id != id }; return n - state.value.size }
    override suspend fun clear(): Int { state.value = emptyList(); return 1 }
    override suspend fun purgeBefore(beforeIso: String): Int { val n = state.value.size; state.value = state.value.filter { it.at >= beforeIso }; return n - state.value.size }
    override suspend fun markReportedByNumber(number: String, category: String): Int {
        state.value = state.value.map { if (it.number == number) it.copy(reported = true, category = category) else it }; return 1
    }
    override suspend fun clearReportedByNumber(number: String): Int {
        state.value = state.value.map { if (it.number == number) it.copy(reported = false, category = null) else it }; return 1
    }
    override fun observeActionCounts(): Flow<List<ActionCount>> = state.map { l ->
        l.filter { !it.simulated }.groupBy { it.action }.map { ActionCount(it.key, it.value.size) }
    }
}

class FakeMetaDao : DatasetMetaDao {
    val state = MutableStateFlow<DatasetMeta?>(null)
    override fun observe(): Flow<DatasetMeta?> = state
    override suspend fun getSync() = state.value
    override suspend fun upsert(meta: DatasetMeta): Long { state.value = meta; return 1 }
    override suspend fun clear(): Int { state.value = null; return 1 }
}

class FakeClock(var now: Long = 1_800_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
    fun advance(ms: Long) { now += ms }
}

class FakePrefsCleaner : LocalPrefsCleaner {
    var cleared = 0
    override suspend fun clear() { cleared++ }
}

/** Monta todos os repositórios sobre DAOs em memória, já com os dados padrão (como o callback do Room). */
class TestEnv {
    val clock = FakeClock()
    val settingsDao = FakeSettingsDao()
    val blocklistDao = FakeBlocklistDao()
    val ruleDao = FakeRuleDao()
    val reputationDao = FakeReputationDao()
    val historyDao = FakeHistoryDao()
    val metaDao = FakeMetaDao()
    val prefs = FakePrefsCleaner()

    val history = HistoryRepository(historyDao, clock)
    val lists = ListRepository(blocklistDao, clock)
    val rules = RulesRepository(ruleDao, clock)
    val settings = SettingsRepository(settingsDao, ruleDao, metaDao)
    val reports = ReportRepository(lists, reputationDao, historyDao, clock)
    val privacy = PrivacyRepository(historyDao, blocklistDao, reputationDao, ruleDao, settingsDao, metaDao, prefs, clock)
    val pipeline = ScreeningPipeline(settingsDao, blocklistDao, ruleDao, reputationDao, historyDao, history, clock)
    val verify = VerifyNumberUseCase(pipeline)

    init {
        settingsDao.state.value = DefaultData.settings()
        metaDao.state.value = DefaultData.meta(clock.now)
        ruleDao.state.value = DefaultData.rules(clock.now)
    }
}
