package br.com.conexus.antispam.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Recebido $action, agendando workers")
            scheduleWorkers(context)
        }
    }

    private fun scheduleWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // Seed worker - roda uma vez se nunca rodou
        val seedRequest = OneTimeWorkRequest.Builder(SeedWorker::class.java)
            .addTag("init")
            .build()
        workManager.enqueueUniqueWork(SeedWorker.WORK_NAME, ExistingWorkPolicy.KEEP, seedRequest)

        // Delta sync - periódico (a cada 6 horas)
        val syncRequest = PeriodicWorkRequest.Builder(DeltaSyncWorker::class.java, 6, TimeUnit.HOURS)
            .addTag("sync")
            .build()
        workManager.enqueueUniquePeriodicWork(DeltaSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, syncRequest)
    }
}