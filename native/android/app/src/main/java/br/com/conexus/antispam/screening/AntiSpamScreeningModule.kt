package br.com.conexus.antispam.screening

import android.app.Activity
import android.content.Context
import android.telecom.TelecomManager
import android.os.Build
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import br.com.conexus.antispam.ConexusCallerIdApplication
import br.com.conexus.antispam.di.ScreeningModule
import br.com.conexus.antispam.decision.CallAction
import br.com.conexus.antispam.decision.CallContext
import br.com.conexus.antispam.decision.CallDecision
import br.com.conexus.antispam.decision.CallPresentation
import br.com.conexus.antispam.decision.DecisionEngine
import br.com.conexus.antispam.decision.EngineInput
import br.com.conexus.antispam.decision.ProtectionMode
import br.com.conexus.antispam.decision.ReputationEntry
import br.com.conexus.antispam.decision.StirShaken
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.database.ReputationEntry as DbReputationEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Módulo nativo de screening (Android 10+).
 * 
 * Em development build (`./gradlew assembleDebug` + `adb install`),
 * este módulo é registrado via Hilt e permite:
 *  - verificar/conceder RoleManager.ROLE_CALL_SCREENING
 *  - receber eventos do CallerIdScreeningService
 *  - notificar decisão do motor JS/Kotlin para o serviço nativo
 * 
 * O serviço de screening real (CallerIdScreeningService) chama o motor de decisão
 * via callback; a decisão volta por notifyDecision. Enquanto o motor não responde,
 * o serviço mantém comportamento fail-safe (ALLOW) — ver THREAT_MODEL.md.
 */
@AndroidEntryPoint
class AntiSpamScreeningModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var screeningCallback: ScreeningCallback

    override fun getName() = "AntiSpamScreening"

    override fun initialize() {
        super.initialize()
        reactContext.addLifecycleEventListener(this)
        // Inicializa Hilt entry point para acessar DI
        val entryPoint = EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            ScreeningModule::class.java
        )
        database = entryPoint.database()
        screeningCallback = entryPoint.screeningCallback()
    }

    override fun onHostResume() {}
    override fun onHostPause() {}
    override fun onHostDestroy() {
        reactContext.removeLifecycleEventListener(this)
    }

    private fun telecom(): TelecomManager? =
        reactContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    @ReactMethod
    fun isRoleHeld(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                promise.resolve(false)
                return
            }
            val tm = telecom()
            val held = tm?.getDefaultDialerPackage() != null &&
                tm.phoneAccountsSupported &&
                reactContext.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            // Verificação canônica do papel:
            val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (reactContext.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager)
                    ?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) ?: false
            } else false
            promise.resolve(roleHeld || held)
        } catch (e: Exception) {
            promise.reject("ROLE_CHECK", e)
        }
    }

    @ReactMethod
    fun requestRole(promise: Promise) {
        try {
            val activity = currentActivity ?: run {
                promise.resolve(false); return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = activity.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                if (rm?.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true) {
                    val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING)
                    activity.startActivityForResult(intent, REQUEST_ROLE)
                    promise.resolve(true) // resultado definitivo chega em isRoleHeld
                    return
                }
            }
            promise.resolve(false)
        } catch (e: Exception) {
            promise.reject("ROLE_REQUEST", e)
        }
    }

    @ReactMethod
    fun openRoleSettings(promise: Promise) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            reactContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ROLE_SETTINGS", e)
        }
    }

    @ReactMethod
    fun notifyDecision(number: String, action: String, promise: Promise) {
        // Entrega o resultado do motor de decisão ao serviço nativo
        screeningCallback.publishDecision(number, action)
        promise.resolve(null)
    }

    @ReactMethod
    fun getDatabaseStats(promise: Promise) {
        // Para debug/UI: retorna contagem de entradas no Room
        kotlinx.coroutines.Dispatchers.IO.plus(SupervisorJob()).launch {
            try {
                val reputationCount = database.reputationDao().count()
                val historyCount = database.historyDao().count()
                val blocklistCount = database.blocklistDao().countBlocked()
                val allowlistCount = database.blocklistDao().countAllowed() // TODO: add method
                
                val writableMap = Arguments.createMap().apply {
                    putInt("reputationEntries", reputationCount.await())
                    putInt("historyEntries", historyCount.await())
                    putInt("blockedNumbers", blocklistCount.await())
                }
                promise.resolve(writableMap)
            } catch (e: Exception) {
                promise.reject("DB_STATS", e)
            }
        }
    }

    @ReactMethod
    fun simulateCall(number: String, promise: Promise) {
        // Para testes no Expo Go / desenvolvimento: simula chamada via motor Kotlin
        kotlinx.coroutines.Dispatchers.IO.plus(SupervisorJob()).launch {
            try {
                val ctx = CallContext(
                    rawNumber = number,
                    presentation = CallPresentation.UNKNOWN,
                    contactName = null,
                    stirShaken = StirShaken.UNKNOWN
                )
                val decision = runDecisionPipeline(ctx)
                
                val writableMap = Arguments.createMap().apply {
                    putString("action", decision.action.name)
                    putInt("score", decision.score)
                    putString("confidence", decision.confidence.name)
                    putString("display", decision.normalized.canonical)
                    putArray("reasons") { reasonsArray ->
                        decision.reasons.forEachIndexed { idx, reason ->
                            reasonsArray.putString(idx, reason.name)
                        }
                    }
                    putArray("explanation") { explArray ->
                        decision.explanation.forEachIndexed { idx, expl ->
                            explArray.putString(idx, expl)
                        }
                    }
                }
                promise.resolve(writableMap)
            } catch (e: Exception) {
                promise.reject("SIMULATE_ERROR", e)
            }
        }
    }

    private suspend fun runDecisionPipeline(ctx: CallContext): CallDecision {
        val settings = database.userSettingsDao().getSync() 
            ?: return DecisionEngine.decide(EngineInput(
                ctx = ctx,
                config = DecisionEngine.MODE_DEFAULTS[ProtectionMode.BALANCED]!!,
                rules = emptyList(),
                allowlist = emptyList(),
                blacklist = emptyList(),
                lookupReputation = { null },
                recentCalls = emptyList()
            ))
        
        val config = buildConfig(settings)
        val allowlist = database.blocklistDao().getAllowed().firstOrNull()?.map { it.number } ?: emptyList()
        val blacklist = database.blocklistDao().getBlocked().firstOrNull()?.map { it.number } ?: emptyList()
        val history = database.historyDao().getRecent(50).firstOrNull() ?: emptyList()
        val recentCalls = history.map { 
            br.com.conexus.antispam.decision.CampaignSignal(it.number, java.time.Instant.parse(it.at).toEpochMilli()) 
        }

        val input = EngineInput(
            ctx = ctx,
            config = config,
            rules = emptyList(), // TODO: carregar regras do banco
            allowlist = allowlist,
            blacklist = blacklist,
            lookupReputation = { canonical ->
                database.reputationDao().get(canonical)?.let { toReputationEntry(it) }
            },
            recentCalls = recentCalls,
            contactNumbers = emptySet(),
            previouslyBlocked = blacklist.toSet(),
            previouslyAllowed = allowlist.toSet()
        )

        return DecisionEngine.decide(input)
    }

    private fun buildConfig(settings: br.com.conexus.antispam.database.UserSettings): DecisionEngineConfig {
        val mode = ProtectionMode.valueOf(settings.mode)
        val baseConfig = DecisionEngine.MODE_DEFAULTS[mode] ?: DecisionEngine.MODE_DEFAULTS[ProtectionMode.BALANCED]!!
        return if (mode == ProtectionMode.CUSTOM) {
            baseConfig.copy(
                blockThreshold = settings.blockThreshold,
                silenceThreshold = settings.silenceThreshold,
                warnThreshold = settings.warnThreshold,
                handle0303 = settings.handle0303,
                blockUnknownInAggressive = settings.blockUnknownInAggressive
            )
        } else baseConfig
    }

    private fun toReputationEntry(entry: DbReputationEntry): ReputationEntry {
        return ReputationEntry(
            key = entry.key,
            country = entry.country,
            category = entry.category,
            score = entry.score,
            reports = entry.reports,
            uniqueReporters = entry.uniqueReporters,
            firstSeen = entry.firstSeen,
            lastSeen = entry.lastSeen,
            confidence = entry.confidence,
            falsePositives = entry.falsePositives
        )
    }

    companion object {
        const val REQUEST_ROLE = 4711
    }
}

/**
 * Callback interface para comunicação ScreeningService ↔ Module
 */
interface ScreeningCallback {
    fun publishDecision(number: String, action: String)
}