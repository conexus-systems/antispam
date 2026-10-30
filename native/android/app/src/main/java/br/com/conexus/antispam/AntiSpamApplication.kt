package br.com.conexus.antispam

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.conexus.antispam.screening.ScreeningNotifier
import br.com.conexus.antispam.sync.DeltaSyncWorker
import br.com.conexus.antispam.sync.HousekeepingWorker
import br.com.conexus.antispam.sync.SeedWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AntiSpamApplication : Application() {

    @Inject lateinit var notifier: ScreeningNotifier

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        scheduleWork()
    }

    /**
     * Trabalho agendado aqui (sem BootReceiver): o WorkManager persiste o agendamento e o reexecuta após
     * reboot/atualização; "unique work" + KEEP/KEEP evita duplicar a cada abertura do processo.
     */
    private fun scheduleWork() {
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniqueWork(
            SeedWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SeedWorker>().addTag("init").build(),
        )
        wm.enqueueUniquePeriodicWork(
            DeltaSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeltaSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("sync")
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            HousekeepingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HousekeepingWorker>(1, TimeUnit.DAYS).addTag("housekeeping").build(),
        )
    }
}
