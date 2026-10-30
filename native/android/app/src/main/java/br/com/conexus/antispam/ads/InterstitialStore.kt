package br.com.conexus.antispam.ads

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adsPrefs by preferencesDataStore(name = "conexus_ads_prefs")

/** Persiste os contadores do [InterstitialPolicy] (arquivo DataStore próprio, separado do resto do app). */
@Singleton
class InterstitialStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val kActions = intPreferencesKey("actions_since_ad")
    private val kLast = longPreferencesKey("last_shown_at")
    private val kWindow = longPreferencesKey("hour_window_start")
    private val kCount = intPreferencesKey("hour_count")

    suspend fun load(): InterstitialState {
        val p = context.adsPrefs.data.first()
        return InterstitialState(p[kActions] ?: 0, p[kLast] ?: 0L, p[kWindow] ?: 0L, p[kCount] ?: 0)
    }

    suspend fun save(s: InterstitialState) {
        context.adsPrefs.edit {
            it[kActions] = s.actionsSinceAd
            it[kLast] = s.lastShownAt
            it[kWindow] = s.hourWindowStart
            it[kCount] = s.hourCount
        }
    }
}
