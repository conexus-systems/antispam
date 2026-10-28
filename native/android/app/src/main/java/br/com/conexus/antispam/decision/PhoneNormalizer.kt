package br.com.conexus.antispam.decision

/** Porte de rust/antispam-core/src/phone/mod.rs (normalize). JVM puro. */
enum class NumberKind {
    MOBILE,
    LANDLINE,
    TOLL_FREE,
    TELEMARKETING,
    SHARED_COST,
    DONATION,
    PREMIUM,
    NON_GEO_UNIQUE,
    EMERGENCY,
    SHORT_CODE,
    INTERNATIONAL,
    LOCAL_NO_DDD,
    HIDDEN,
    INVALID,
}

data class NormalizedNumber(
    val input: String,
    val kind: NumberKind,
    val e164: String? = null,
    val ddd: String? = null,
    val shard: String? = null,
)

object PhoneNormalizer {

    private val VALID_DDDS = setOf(
        "11", "12", "13", "14", "15", "16", "17", "18", "19",
        "21", "22", "24", "27", "28",
        "31", "32", "33", "34", "35", "37", "38",
        "41", "42", "43", "44", "45", "46", "47", "48", "49",
        "51", "53", "54", "55",
        "61", "62", "63", "64", "65", "66", "67", "68", "69",
        "71", "73", "74", "75", "77", "79",
        "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "91", "92", "93", "94", "95", "96", "97", "98", "99",
    )

    private val HIDDEN_TOKENS = setOf(
        "", "anonymous", "private", "unknown", "restricted", "privado", "desconhecido",
    )

    private val NON_GEO_PREFIXES = listOf(
        "0800" to NumberKind.TOLL_FREE,
        "0303" to NumberKind.TELEMARKETING,
        "0300" to NumberKind.SHARED_COST,
        "0500" to NumberKind.DONATION,
        "0900" to NumberKind.PREMIUM,
    )

    private val NON_GEO_UNIQUE_REGEX =
        Regex("^(300[0-9]|400[0-9]|4020|4062|4090|4091)\\d{4}$")

    fun normalize(raw: String?, userDdd: String? = null): NormalizedNumber {
        val input = raw ?: ""
        val trimmed = input.trim()

        if (trimmed.lowercase() in HIDDEN_TOKENS) return build(input, NumberKind.HIDDEN)

        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it in '0'..'9' }

        if (digits.isEmpty()) return build(input, NumberKind.HIDDEN)

        if (isEmergencyInAnyForm(digits)) return build(input, NumberKind.EMERGENCY)

        if (hasPlus) {
            if (digits.startsWith("55")) {
                val rest = digits.substring(2)
                nonGeo(input, "0$rest")?.let { return it }
                if (NON_GEO_UNIQUE_REGEX.containsMatchIn(rest)) {
                    return build(input, NumberKind.NON_GEO_UNIQUE, "+55$rest")
                }
                return fromNational(input, rest)
            }
            if (digits.length in 8..15) return build(input, NumberKind.INTERNATIONAL, "+$digits")
            return build(input, NumberKind.INVALID)
        }

        if (digits.length <= 5) {
            if (isEmergencyOrUtility(digits)) return build(input, NumberKind.EMERGENCY)
            if (digits.length >= 4) return build(input, NumberKind.SHORT_CODE)
            return build(input, NumberKind.INVALID)
        }

        // Prefixo 00 (discagem internacional)
        if (digits.startsWith("00")) {
            val intl = digits.substring(4)
            if (intl.startsWith("55")) return normalize("+$intl", userDdd)
            if (intl.length in 8..15) return build(input, NumberKind.INTERNATIONAL, "+$intl")
            return build(input, NumberKind.INVALID)
        }

        // Começa com 0 (não geográfico ou nacional com tronco)
        if (digits.startsWith("0")) {
            nonGeo(input, digits)?.let { return it }
            if (digits.length == 11 || digits.length == 12) return fromNational(input, digits.substring(1))
            if (digits.length == 13 || digits.length == 14) return fromNational(input, digits.substring(3))
            return build(input, NumberKind.INVALID)
        }

        if (NON_GEO_UNIQUE_REGEX.containsMatchIn(digits)) {
            return build(input, NumberKind.NON_GEO_UNIQUE, "+55$digits")
        }

        if (digits.startsWith("55") && (digits.length == 12 || digits.length == 13)) {
            return fromNational(input, digits.substring(2))
        }

        if (digits.length == 10 || digits.length == 11) return fromNational(input, digits)

        // Local sem DDD (8 ou 9 dígitos)
        if (digits.length == 8 || digits.length == 9) {
            if (userDdd != null && userDdd in VALID_DDDS) return fromNational(input, userDdd + digits)
            return build(input, NumberKind.LOCAL_NO_DDD)
        }

        return build(input, NumberKind.INVALID)
    }

    private fun isEmergencyOrUtility(digits: String): Boolean {
        if (digits == "911") return true
        if (digits.length == 3 && digits.startsWith("1")) return true
        return (digits.length == 4 || digits.length == 5) && digits.startsWith("10")
    }

    private fun isEmergencyInAnyForm(digits: String): Boolean {
        if (digits.length > 8) return false
        val withoutCountry = if (digits.startsWith("55") && digits.length > 3) digits.substring(2) else digits
        val candidates = listOf(digits, digits.trimStart('0'), withoutCountry.trimStart('0'))
        return candidates.any { it.length in 3..5 && isEmergencyOrUtility(it) }
    }

    private fun build(
        input: String,
        kind: NumberKind,
        e164: String? = null,
        ddd: String? = null,
    ): NormalizedNumber {
        val shard = e164?.let {
            when (kind) {
                NumberKind.MOBILE, NumberKind.LANDLINE -> ddd?.let { d -> "55-$d" }
                NumberKind.INTERNATIONAL -> "55-intl"
                else -> "55-ng"
            }
        }
        return NormalizedNumber(input, kind, e164, ddd, shard)
    }

    private fun fromNational(input: String, national: String): NormalizedNumber {
        if (national.length != 10 && national.length != 11) return build(input, NumberKind.INVALID)
        val ddd = national.substring(0, 2)
        if (ddd !in VALID_DDDS) return build(input, NumberKind.INVALID)
        val sub = national.substring(2)
        if (sub.length == 9) {
            if (!sub.startsWith("9")) return build(input, NumberKind.INVALID)
            return build(input, NumberKind.MOBILE, "+55$ddd$sub", ddd)
        }
        val first = sub[0]
        if (first in '2'..'6') return build(input, NumberKind.LANDLINE, "+55$ddd$sub", ddd)
        if (first in '7'..'9') return build(input, NumberKind.MOBILE, "+55${ddd}9$sub", ddd)
        return build(input, NumberKind.INVALID)
    }

    private fun nonGeo(input: String, digits: String): NormalizedNumber? {
        for ((prefix, kind) in NON_GEO_PREFIXES) {
            if (digits.startsWith(prefix) && (digits.length == 11 || digits.length == 10)) {
                // Remove o zero inicial para o E.164
                return build(input, kind, "+55${digits.substring(1)}")
            }
        }
        return null
    }
}
