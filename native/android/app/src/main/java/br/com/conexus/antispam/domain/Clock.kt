package br.com.conexus.antispam.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Fonte de tempo injetável (testes usam um relógio fixo). */
fun interface Clock {
    fun nowMillis(): Long
}

object WallClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** ISO 8601 UTC de formato fixo com milissegundos: ordenável como texto (índices e purge dependem disso). */
object IsoTime {
    private val FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun format(epochMillis: Long): String = FORMAT.format(Instant.ofEpochMilli(epochMillis))

    /** Aceita qualquer ISO 8601 com 'Z'. Devolve 0 se inválido. */
    fun parse(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}
