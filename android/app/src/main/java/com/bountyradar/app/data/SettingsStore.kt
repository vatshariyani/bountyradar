package com.bountyradar.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bountyradar.app.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bountyradar_settings")

/** Persists the theme choice and bookmarked program ids via DataStore. */
class SettingsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val bookmarksKey = stringSetPreferencesKey("bookmarks")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    val bookmarks: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[bookmarksKey] ?: emptySet()
    }

    suspend fun toggleBookmark(docId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[bookmarksKey] ?: emptySet()
            prefs[bookmarksKey] =
                if (docId in current) current - docId else current + docId
        }
    }
}
