package br.com.conexus.antispam.database

import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.conexus.antispam.domain.IsoTime

/**
 * Dados padrão do banco: configurações, linha de dataset_meta e regras de sistema.
 * Sem números de pessoas físicas. Usado pelo callback de criação do Room e por PrivacyRepository.
 */
object DefaultData {

    fun settings(): UserSettings = UserSettings()

    fun meta(nowMillis: Long): DatasetMeta = DatasetMeta(
        id = 1,
        version = 0,
        publishedAt = "",
        signatureOk = false,
        source = "seed",
        entries = 0,
        installedAt = IsoTime.format(nowMillis),
    )

    /** Regras de sistema: 0303 -> SILENCE (ligada); ocultos e internacionais -> BLOCK (desligadas até o usuário ligar). */
    fun rules(nowMillis: Long): List<RuleEntity> {
        val created = IsoTime.format(nowMillis)
        val base = SystemRules.SYSTEM_PRIORITY_BASE
        return listOf(
            RuleEntity(SystemRules.ZERO_THREE_ZERO_ID, "Telemarketing 0303", true, "PREFIX", "+55303", "SILENCE", base, created),
            RuleEntity(SystemRules.HIDDEN_ID, "Números ocultos", false, "HIDDEN", "", "BLOCK", base + 1, created),
            RuleEntity(SystemRules.INTERNATIONAL_ID, "Números internacionais", false, "INTERNATIONAL", "", "BLOCK", base + 2, created),
        )
    }

    /** INSERT OR IGNORE: idempotente, serve a onCreate e a onOpen (após migração destrutiva). */
    fun insertDefaults(db: SupportSQLiteDatabase, nowMillis: Long) {
        val s = settings()
        db.execSQL(
            "INSERT OR IGNORE INTO user_settings (id, mode, blockThreshold, silenceThreshold, warnThreshold, handle0303, " +
                "blockUnknownInAggressive, theme, accent, reduceMotion, notificationsEnabled, onboardingDone, datasetVersion, " +
                "blockHidden, blockInternational, historyRetentionDays, consentShareReports, rejectBlockedCalls) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                s.id, s.mode, s.blockThreshold, s.silenceThreshold, s.warnThreshold, s.handle0303,
                s.blockUnknownInAggressive.toInt(), s.theme, s.accent, s.reduceMotion.toInt(),
                s.notificationsEnabled.toInt(), s.onboardingDone.toInt(), s.datasetVersion,
                s.blockHidden.toInt(), s.blockInternational.toInt(), s.historyRetentionDays,
                s.consentShareReports.toInt(), s.rejectBlockedCalls.toInt(),
            ),
        )
        val m = meta(nowMillis)
        db.execSQL(
            "INSERT OR IGNORE INTO dataset_meta (id, version, publishedAt, signatureOk, source, entries, installedAt) " +
                "VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any?>(m.id, m.version, m.publishedAt, m.signatureOk.toInt(), m.source, m.entries, m.installedAt),
        )
        for (r in rules(nowMillis)) {
            db.execSQL(
                "INSERT OR IGNORE INTO rules (id, name, enabled, kind, value, action, priority, createdAt) VALUES (?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(r.id, r.name, r.enabled.toInt(), r.kind, r.value, r.action, r.priority, r.createdAt),
            )
        }
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0
}
