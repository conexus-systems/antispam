package br.com.conexus.antispam.domain

import br.com.conexus.antispam.decision.NormalizedNumber
import br.com.conexus.antispam.decision.NumberKind

/** Formatação de número para exibição (pt-BR). */
object PhoneFormat {

    private val NON_GEO = setOf(
        NumberKind.TOLL_FREE, NumberKind.TELEMARKETING, NumberKind.SHARED_COST,
        NumberKind.DONATION, NumberKind.PREMIUM,
    )

    /** Chave armazenada para histórico/listas: E.164 se houver, senão o texto original aparado. */
    fun key(n: NormalizedNumber): String = n.e164 ?: n.input.trim()

    fun display(n: NormalizedNumber): String {
        if (n.kind == NumberKind.HIDDEN) return "Número oculto"
        val e = n.e164 ?: return n.input.trim().ifEmpty { "Número oculto" }
        if (e.startsWith("+55")) {
            val rest = e.substring(3)
            return when {
                n.kind == NumberKind.MOBILE && rest.length == 11 ->
                    "+55 (${rest.substring(0, 2)}) ${rest.substring(2, 7)}-${rest.substring(7)}"
                n.kind == NumberKind.LANDLINE && rest.length == 10 ->
                    "+55 (${rest.substring(0, 2)}) ${rest.substring(2, 6)}-${rest.substring(6)}"
                n.kind == NumberKind.NON_GEO_UNIQUE && rest.length == 8 -> "${rest.substring(0, 4)}-${rest.substring(4)}"
                n.kind in NON_GEO && rest.length >= 9 -> "0${rest.substring(0, 3)} ${rest.substring(3, 6)} ${rest.substring(6)}"
                else -> e
            }
        }
        return e
    }
}
