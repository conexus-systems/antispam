package br.com.conexus.antispam.ui.history

import br.com.conexus.antispam.domain.HistoryFilter
import br.com.conexus.antispam.domain.HistoryItem

/** Filtros da tela; "Denunciadas" não existe no repositório e é aplicado sobre a lista completa. */
enum class HistoryTab(val source: HistoryFilter) {
    ALL(HistoryFilter.ALL),
    BLOCKED(HistoryFilter.BLOCKED),
    SILENCED(HistoryFilter.SILENCED),
    ALLOWED(HistoryFilter.ALLOWED),
    REPORTED(HistoryFilter.ALL),
    ;

    fun apply(items: List<HistoryItem>): List<HistoryItem> =
        if (this == REPORTED) items.filter { it.reported } else items
}
