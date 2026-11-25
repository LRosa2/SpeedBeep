package io.github.lrosa2.speedbeep.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.lrosa2.speedbeep.engine.Settings
import io.github.lrosa2.speedbeep.engine.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

/** DataStore Preferences behind [SettingsCodec] (spec §3: settings live in DataStore). */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it) }
        .distinctUntilChanged()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val next = transform(decode(prefs)).clamped()
            prefs.clear()
            for ((key, value) in SettingsCodec.encode(next)) {
                when (value) {
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                    else -> error("unsupported settings value for $key")
                }
            }
        }
    }

    private fun decode(prefs: Preferences) = SettingsCodec.decode(prefs.asMap().mapKeys { it.key.name })
}
