package br.com.conexus.antispam.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.database.ReputationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import kotlinx.serialization.json.Json

/**
 * Worker para inicializar o dataset seed assinado (Ed25519) no primeiro boot.
 * O dataset vem como asset assinado no APK.
 */
class SeedWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SeedWorker"
        const val DATASET_ASSET = "dataset_seed.json"
        const val PUBLIC_KEY_ASSET = "dataset_public_key.der" // Ed25519 public key PKCS#8
        const val WORK_NAME = "seed_dataset_init"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val currentVersion = db.userSettingsDao().getSync()?.datasetVersion ?: 0L

            // Se já tem versão > 0, pular (já inicializado)
            if (currentVersion > 0) {
                Log.i(TAG, "Dataset já inicializado (v$currentVersion)")
                return@withContext Result.success()
            }

            // Carregar dataset do asset
            val datasetJson = loadAssetString(DATASET_ASSET) ?: return@withContext Result.failure()
            val publicKeyBytes = loadAssetBytes(PUBLIC_KEY_ASSET) ?: return@withContext Result.failure()

            // Parse e verificação
            val manifest = Json.decodeFromString<DatasetManifest>(datasetJson)
            val entriesJson = manifest.entriesJson

            // Verificar assinatura Ed25519
            if (!verifyEd25519(entriesJson, manifest.signature, publicKeyBytes)) {
                Log.e(TAG, "Falha na verificação Ed25519 do dataset seed")
                return@withContext Result.failure()
            }

            // Verificar digest SHA256
            val digest = sha256Hex(entriesJson)
            if (digest != manifest.digest) {
                Log.e(TAG, "Digest SHA256 não confere")
                return@withContext Result.failure()
            }

            // Parse entries
            val entries = Json.decodeFromString<List<SeedEntry>>(entriesJson)
            val reputationEntries = entries.map { it.toReputationEntry() }

            // Inserir no banco
            db.reputationDao().upsertAll(reputationEntries)

            // Atualizar versão no settings
            val settings = db.userSettingsDao().getSync() ?: br.com.conexus.antispam.database.UserSettings()
            val updatedSettings = settings.copy(datasetVersion = manifest.version.toLong())
            db.userSettingsDao().upsert(updatedSettings)

            Log.i(TAG, "Dataset seed v${manifest.version} carregado: ${entries.size} entradas")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar dataset seed", e)
            Result.failure()
        }
    }

    private fun loadAssetString(name: String): String? {
        return applicationContext.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun loadAssetBytes(name: String): ByteArray? {
        return applicationContext.assets.open(name).readAllBytes()
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun verifyEd25519(data: String, signatureHex: String, publicKeyBytes: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(data.toByteArray())
            signature.verify(signatureHex.hexToByteArray())
        } catch (e: Exception) {
            false
        }
    }

    @kotlinx.serialization.Serializable
    private data class DatasetManifest(
        val version: Int,
        val entries: Int,
        val digest: String,
        val signature: String,
        val entriesJson: String
    )

    @kotlinx.serialization.Serializable
    private data class SeedEntry(
        val key: String,
        val country: String,
        val category: String,
        val score: Int,
        val reports: Int,
        val uniqueReporters: Int,
        val firstSeen: String,
        val lastSeen: String,
        val confidence: Double = 0.5,
        val falsePositives: Int = 0
    ) {
        fun toReputationEntry(): ReputationEntry = ReputationEntry(
            key = key,
            country = country,
            category = category,
            score = score,
            reports = reports,
            uniqueReporters = uniqueReporters,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            confidence = confidence,
            falsePositives = falsePositives
        )
    }
}

private fun String.hexToByteArray(): ByteArray {
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}