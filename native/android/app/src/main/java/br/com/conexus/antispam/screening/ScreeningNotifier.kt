package br.com.conexus.antispam.screening

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.conexus.antispam.R
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.domain.ScreeningResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificação silenciosa "Chamada bloqueada/silenciada/com alerta". Sem POST_NOTIFICATIONS não notifica
 * (e o bloqueio continua). Nunca contém anúncio (regra A1).
 */
@Singleton
class ScreeningNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Chamadas analisadas", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Avisos silenciosos sobre chamadas bloqueadas, silenciadas ou com alerta"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /** Devolve true se publicou a notificação. */
    fun notify(result: ScreeningResult): Boolean {
        if (!result.policy.notify) return false
        val title = when (result.decision.action) {
            CallAction.BLOCK -> "Chamada bloqueada"
            CallAction.SILENCE -> "Chamada silenciada"
            CallAction.WARN -> "Chamada com sinais de risco"
            CallAction.ALLOW -> return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = open?.let {
            PendingIntent.getActivity(
                context, 0, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(result.displayNumber)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.displayNumber + "\n" + result.explanation.firstOrNull().orEmpty()))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        return try {
            nm.notify((result.atMillis % Int.MAX_VALUE).toInt(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "screening_decisions"
    }
}
