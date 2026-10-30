package br.com.conexus.antispam.sync

import kotlinx.serialization.builtins.ListSerializer
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.conexus.antispam.database.AppDatabase
import br.com.conexus.antispam.database.ReputationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import kotlinx.serialization.json.Json

/**
 * Worker para sync periódico de deltas assinados do backend.
 * Baixa manifest -> verifica assinatura -> baixa delta -> aplica.
 */
class DeltaSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DeltaSyncWorker"
        const val WORK_NAME = "delta_sync"
        const val MANIFEST_URL = "https://api.conexus-systems.com.br/v1/datasets/latest"
        const val DELTA_URL_TEMPLATE = "https://api.conexus-systems.com.br/v1/datasets/%d/delta"
        const val PUBLIC_KEY_HEX = "..." // Chave pública Ed25519 em hex (configurável via BuildConfig)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val settings = db.userSettingsDao().getSync() ?: return@withContext Result.success()
            val currentVersion = settings.datasetVersion

            // 1. Buscar manifest mais recente
            val manifest = fetchManifest() ?: return@withContext Result.retry()
            if (manifest.version <= currentVersion) {
                Log.i(TAG, "Nenhum delta novo (atual: $currentVersion, servidor: ${manifest.version})")
                return@withContext Result.success()
            }

            // 2. Baixar delta
            val delta = fetchDelta(manifest.version) ?: return@withContext Result.retry()

            // 3. Verificar assinatura e digest
            if (!verifyDelta(delta, manifest)) {
                Log.e(TAG, "Falha na verificação do delta v${manifest.version}")
                return@withContext Result.failure()
            }

            // 4. Aplicar delta
            val applied = applyDelta(db, delta.entries)
            Log.i(TAG, "Delta v${manifest.version} aplicado: $applied entradas")

            // 5. Atualizar versão
            val updatedSettings = settings.copy(datasetVersion = manifest.version.toLong())
            db.userSettingsDao().upsert(updatedSettings)
            db.datasetMetaDao().upsert(
                br.com.conexus.antispam.database.DatasetMeta(
                    id = 1,
                    version = manifest.version.toLong(),
                    publishedAt = "",
                    signatureOk = true,
                    source = "delta",
                    entries = applied,
                    installedAt = br.com.conexus.antispam.domain.IsoTime.format(System.currentTimeMillis()),
                ),
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erro no sync de delta", e)
            Result.retry()
        }
    }

    private data class DatasetManifest(
        val version: Int,
        val entries: Int,
        val digest: String,
        val signature: String,
        val publicKey: String
    )

    private data class DeltaResponse(
        val version: Int,
        val entries: List<DeltaEntry>
    )

    @kotlinx.serialization.Serializable
    private data class DeltaEntry(
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
    )

    private suspend fun fetchManifest(): DatasetManifest? {
        // TODO: Implementar com Retrofit/OkHttp real
        // Por enquanto retorna null para não falhar build
        return null
    }

    private suspend fun fetchDelta(version: Int): DeltaResponse? {
        // TODO: Implementar com Retrofit/OkHttp real
        return null
    }

    private fun verifyDelta(delta: DeltaResponse, manifest: DatasetManifest): Boolean {
        val json = Json.encodeToString(ListSerializer(DeltaEntry.serializer()), delta.entries)
        val digest = sha256Hex(json)
        if (digest != manifest.digest) return false

        val publicKeyBytes = manifest.publicKey.hexToByteArray()
        return verifyEd25519(json, manifest.signature, publicKeyBytes)
    }

    private suspend fun applyDelta(db: AppDatabase, entries: List<DeltaEntry>): Int {
        val reputationEntries = entries.map { it.toReputationEntry() }
        db.reputationDao().upsertAll(reputationEntries)
        return entries.size
    }

    private fun DeltaEntry.toReputationEntry(): ReputationEntry = ReputationEntry(
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
}

private fun String.hexToByteArray(): ByteArray {
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}