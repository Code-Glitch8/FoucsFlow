package com.focusflow.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusflow.app.data.db.SessionMode
import com.focusflow.app.domain.ShortsMode
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
        val shortsMode = stringPreferencesKey("youtube_shorts_mode")
        val homeRedirect = booleanPreferencesKey("youtube_home_redirect")
        val floatingEnabled = booleanPreferencesKey("floating_enabled")
        val bubbleX = intPreferencesKey("bubble_x")
        val bubbleY = intPreferencesKey("bubble_y")
    }

    private val store get() = context.settingsStore

    val themeMode: Flow<ThemeMode> = store.data.map { it.enumOrDefault(Keys.theme, ThemeMode.DARK) }
    val defaultMode: Flow<SessionMode> = store.data.map { it.enumOrDefault(Keys.defaultMode, SessionMode.TIMED) }
    val defaultDurationMin: Flow<Int> = store.data.map { it[Keys.defaultDurationMin] ?: 25 }

    val shortsMode: Flow<ShortsMode> = store.data.map { it.enumOrDefault(Keys.shortsMode, ShortsMode.FOCUS_ONLY) }
    val homeRedirect: Flow<Boolean> = store.data.map { it[Keys.homeRedirect] ?: false }

    val floatingEnabled: Flow<Boolean> = store.data.map { it[Keys.floatingEnabled] ?: true }

    /** Last bubble position in pixels, or null if the user never moved it. */
    val bubblePosition: Flow<Pair<Int, Int>?> = store.data.map {
        val x = it[Keys.bubbleX]
        val y = it[Keys.bubbleY]
        if (x != null && y != null) x to y else null
    }

    suspend fun setFloatingEnabled(enabled: Boolean) = store.edit { it[Keys.floatingEnabled] = enabled }
    suspend fun setBubblePosition(x: Int, y: Int) = store.edit { it[Keys.bubbleX] = x; it[Keys.bubbleY] = y }
    suspend fun resetBubblePosition() = store.edit { it.remove(Keys.bubbleX); it.remove(Keys.bubbleY) }

    suspend fun setShortsMode(mode: ShortsMode) = store.edit { it[Keys.shortsMode] = mode.name }
    suspend fun setHomeRedirect(enabled: Boolean) = store.edit { it[Keys.homeRedirect] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDefaultMode(mode: SessionMode) = store.edit { it[Keys.defaultMode] = mode.name }
    suspend fun setDefaultDurationMin(minutes: Int) =
        store.edit { it[Keys.defaultDurationMin] = minutes.coerceIn(1, 600) }
}

private inline fun <reified E : Enum<E>> Preferences.enumOrDefault(
    key: Preferences.Key<String>,
    default: E,
): E = this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
