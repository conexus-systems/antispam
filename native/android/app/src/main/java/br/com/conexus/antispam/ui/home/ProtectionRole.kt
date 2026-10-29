package br.com.conexus.antispam.ui.home

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/** Papel de triagem de chamadas (ROLE_CALL_SCREENING). minSdk = 29, então o RoleManager sempre existe. */
object ProtectionRole {
    fun isHeld(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun isAvailable(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    /** null quando o aparelho não oferece o papel. */
    fun requestIntent(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /** Em Android 13+ a permissão é de execução; antes disso, notificações já estão liberadas. */
    fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Devolve uma função que abre o diálogo do sistema do papel de triagem. [onResult] recebe se o papel
 * está ativo ao voltar (relido do RoleManager, não do resultCode). Se o aparelho não oferece o papel,
 * [onResult] é chamado com false na hora.
 */
@Composable
fun rememberRoleRequester(onResult: (held: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        callback(ProtectionRole.isHeld(context))
    }
    return {
        val intent = ProtectionRole.requestIntent(context)
        if (intent == null) callback(false) else launcher.launch(intent)
    }
}

/** Permissão POST_NOTIFICATIONS pedida em contexto. [onResult] recebe true se concedida. */
@Composable
fun rememberNotificationRequester(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callback(granted)
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            callback(ProtectionRole.notificationsGranted(context))
        }
    }
}

/** Executa [action] sempre que a tela volta ao primeiro plano (o papel pode ter mudado nas configurações). */
@Composable
fun OnResume(action: () -> Unit) {
    val current by rememberUpdatedState(action)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
