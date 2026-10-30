package br.com.conexus.antispam.ui.history

import java.util.Calendar
import java.util.Locale

/** Hora relativa em PT-BR, sem depender de Android (testável na JVM). */
sealed class RelativeTime {
    object Now : RelativeTime()
    data class MinutesAgo(val minutes: Int) : RelativeTime()
    data class HoursAgo(val hours: Int) : RelativeTime()
    /** Ontem às [time] (HH:mm). */
    data class Yesterday(val time: String) : RelativeTime()
    /** Data completa "dd/MM HH:mm". */
    data class Absolute(val text: String) : RelativeTime()
}

object RelativeTimes {
    fun of(atMillis: Long, nowMillis: Long, calendar: Calendar = Calendar.getInstance()): RelativeTime {
        val diff = nowMillis - atMillis
        if (diff < 60_000L) return RelativeTime.Now // inclui relógio no futuro
        if (diff < 3_600_000L) return RelativeTime.MinutesAgo((diff / 60_000L).toInt())
        val dayOfAt = dayStart(atMillis, calendar)
        val dayOfNow = dayStart(nowMillis, calendar)
        val days = Math.round((dayOfNow - dayOfAt) / 86_400_000.0).toInt()
        return when {
            days == 0 -> RelativeTime.HoursAgo((diff / 3_600_000L).toInt())
            days == 1 -> RelativeTime.Yesterday(hhmm(atMillis, calendar))
            else -> RelativeTime.Absolute(
                String.format(Locale.US, "%02d/%02d %s", day(atMillis, calendar), month(atMillis, calendar), hhmm(atMillis, calendar)),
            )
        }
    }

    private fun dayStart(millis: Long, cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun at(millis: Long, cal: Calendar): Calendar = (cal.clone() as Calendar).also { it.timeInMillis = millis }
    private fun day(m: Long, cal: Calendar) = at(m, cal).get(Calendar.DAY_OF_MONTH)
    private fun month(m: Long, cal: Calendar) = at(m, cal).get(Calendar.MONTH) + 1
    private fun hhmm(m: Long, cal: Calendar): String {
        val c = at(m, cal)
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
