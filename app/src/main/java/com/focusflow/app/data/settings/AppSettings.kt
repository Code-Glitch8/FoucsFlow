package com.focusflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "focusflow_settings")

/** User preferences that are not relational data live in DataStore. */
class AppSettings(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val defaultMode = stringPreferencesKey("default_session_mode")
        val defaultDurationMin = intPreferencesKey("default_duration_min")
    }

    private val store get() = context.settingsStore

    val themeMode: Flow<ThemeMode> = store.data.map { it.enumOrDefault(Keys.theme, ThemeMode.DARK) }
    val defaultMode: Flow<SessionMode> = store.data.map { it.enumOrDefault(Keys.defaultMode, SessionMode.TIMED) }
    val defaultDurationMin: Flow<Int> = store.data.map { it[Keys.defaultDurationMin] ?: 25 }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDefaultMode(mode: SessionMode) = store.edit { it[Keys.defaultMode] = mode.name }
    suspend fun setDefaultDurationMin(minutes: Int) =
        store.edit { it[Keys.defaultDurationMin] = minutes.coerceIn(1, 600) }
}

private inline fun <reified E : Enum<E>> Preferences.enumOrDefault(
    key: Preferences.Key<String>,
    default: E,
): E = this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
