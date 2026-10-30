package br.com.conexus.antispam.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.domain.IsoTime
import br.com.conexus.antispam.database.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diário: aplica a retenção do histórico (historyRetentionDays). Offline, sem rede. */
class HousekeepingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val days = (db.userSettingsDao().getSync() ?: UserSettings()).historyRetentionDays
            if (days > 0) {
                val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                val removed = db.historyDao().purgeBefore(IsoTime.format(cutoff))
                Log.i(TAG, "Retenção de $days dias: $removed entradas removidas")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erro na limpeza do histórico", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "HousekeepingWorker"
        const val WORK_NAME = "history_housekeeping"
    }
}
