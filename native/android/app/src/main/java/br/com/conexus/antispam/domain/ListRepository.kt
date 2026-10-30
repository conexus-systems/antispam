package br.com.conexus.antispam.domain

import br.com.conexus.antispam.database.BlocklistDao
import br.com.conexus.antispam.database.BlocklistEntry
import br.com.conexus.antispam.decision.NumberKind
import br.com.conexus.antispam.decision.PhoneNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Resultado de adicionar à lista bloqueada ou permitida. */
sealed class ListResult {
    /** [previous] = entrada anterior do mesmo número (ex.: estava na outra lista); use em [ListRepository.undo]. */
    data class Added(val entry: BlocklistEntry, val previous: BlocklistEntry?) : ListResult()
    /** Texto não é um número normalizável (ou sem DDD). Mensagem: "Número inválido". */
    object Invalid : ListResult()
    /** Já está na mesma lista. Mensagem: "Já está na lista". */
    object Duplicate : ListResult()
    /** Emergência nunca entra nas listas. */
    object Emergency : ListResult()
}

/**
 * Listas bloqueada e permitida (RF-LIST-01/02/03). Mutuamente exclusivas: um número
 * está em no máximo uma delas (PK única na tabela).
 */
@Singleton
class ListRepository @Inject constructor(
    private val dao: BlocklistDao,
    private val clock: Clock,
) {

    fun observeBlocked(): Flow<List<ListItem>> = dao.observeByAction(BLOCK).map { it.map(::toItem) }

    fun observeAllowed(): Flow<List<ListItem>> = dao.observeByAction(ALLOW).map { it.map(::toItem) }

    /** Digitado pelo usuário (aceita máscara) -> E.164, ou null se inválido. */
    fun normalize(raw: String): String? = PhoneNormalizer.normalize(raw).e164

    suspend fun status(rawOrE164: String): ListStatus {
        val e164 = normalize(rawOrE164) ?: return ListStatus.NONE
        return when (dao.get(e164)?.action) {
            BLOCK -> ListStatus.BLOCKED
            ALLOW -> ListStatus.ALLOWED
            else -> ListStatus.NONE
        }
    }

    /** Bloqueia. Se estava em permitidos, move (exclusividade). */
    suspend fun block(raw: String, note: String? = null, source: String = "local"): ListResult =
        add(raw, BLOCK, note, source)

    /** Permite ("Desbloquear / Não é spam" também usa isto). Se estava em bloqueados, move. */
    suspend fun allow(raw: String, note: String? = null, source: String = "local"): ListResult =
        add(raw, ALLOW, note, source)

    /** Tira o número de qualquer lista. */
    suspend fun remove(rawOrE164: String) {
        val e164 = normalize(rawOrE164) ?: rawOrE164
        dao.remove(e164)
    }

    /** Desfaz um [ListResult.Added] (barra "Desfazer"): restaura a entrada anterior ou remove. */
    suspend fun undo(added: ListResult.Added) {
        if (added.previous != null) dao.upsert(added.previous) else dao.remove(added.entry.number)
    }

    /** Desfaz uma remoção: reinsere a entrada que a UI guardou. */
    suspend fun restore(entry: BlocklistEntry) { dao.upsert(entry) }

    suspend fun get(rawOrE164: String): BlocklistEntry? = normalize(rawOrE164)?.let { dao.get(it) }

    private suspend fun add(raw: String, action: String, note: String?, source: String): ListResult {
        val n = PhoneNormalizer.normalize(raw)
        if (n.kind == NumberKind.EMERGENCY) return ListResult.Emergency
        val e164 = n.e164 ?: return ListResult.Invalid
        val previous = dao.get(e164)
        if (previous != null && previous.action == action) return ListResult.Duplicate
        val entry = BlocklistEntry(
            number = e164,
            action = action,
            source = source,
            createdAt = IsoTime.format(clock.nowMillis()),
            note = note,
        )
        dao.upsert(entry)
        return ListResult.Added(entry, previous)
    }

    private fun toItem(e: BlocklistEntry) = ListItem(
        number = e.number,
        display = PhoneFormat.display(PhoneNormalizer.normalize(e.number)),
        note = e.note,
        source = e.source,
        createdAtMillis = IsoTime.parse(e.createdAt),
    )

    companion object {
        const val BLOCK = "block"
        const val ALLOW = "allow"
    }
}
