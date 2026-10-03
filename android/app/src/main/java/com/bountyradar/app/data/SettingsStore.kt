package com.bountyradar.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bountyradar.app.ui.SortBy
import com.bountyradar.app.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bountyradar_settings")

/** Persists theme, sort order, bookmarks, muted platforms and per-program notes. */
class SettingsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val sortKey = stringPreferencesKey("sort_by")
    private val bookmarksKey = stringSetPreferencesKey("bookmarks")
    private val mutedKey = stringSetPreferencesKey("muted_platforms")
    private fun noteKey(docId: String) = stringPreferencesKey("note_$docId")
    private val lastVisitKey = longPreferencesKey("last_visit")
    private val visitBaseKey = longPreferencesKey("visit_base")

    /**
     * Returns the moment to count "new since your last visit" from, and records
     * this visit. Re-opening within 30 minutes counts as the same visit.
     */
    suspend fun beginVisit(now: Long): Long {
        var base = 0L
        context.dataStore.edit { prefs ->
            val last = prefs[lastVisitKey] ?: 0L
            base = prefs[visitBaseKey] ?: 0L
            if (now - last > 30 * 60_000L) {
                base = if (last == 0L) now - 86_400_000L else last
                prefs[visitBaseKey] = base
                prefs[lastVisitKey] = now
            }
        }
        return if (base == 0L) now - 86_400_000L else base
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    val sortBy: Flow<SortBy> = context.dataStore.data.map { prefs ->
        runCatching { SortBy.valueOf(prefs[sortKey] ?: SortBy.BEST.name) }.getOrDefault(SortBy.BEST)
    }.distinctUntilChanged()

    suspend fun setSortBy(sort: SortBy) {
        context.dataStore.edit { it[sortKey] = sort.name }
    }

    val bookmarks: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[bookmarksKey] ?: emptySet()
    }.distinctUntilChanged()

    suspend fun toggleBookmark(docId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[bookmarksKey] ?: emptySet()
            prefs[bookmarksKey] = if (docId in current) current - docId else current + docId
        }
    }

    /** Platforms the user does NOT want push alerts for. */
    val mutedPlatforms: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[mutedKey] ?: emptySet()
    }.distinctUntilChanged()

    suspend fun toggleMuted(platform: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[mutedKey] ?: emptySet()
            prefs[mutedKey] = if (platform in current) current - platform else current + platform
        }
    }

    fun note(docId: String): Flow<String> = context.dataStore.data
        .map { it[noteKey(docId)] ?: "" }.distinctUntilChanged()

    suspend fun setNote(docId: String, text: String) {
        context.dataStore.edit { prefs ->
            if (text.isBlank()) prefs.remove(noteKey(docId)) else prefs[noteKey(docId)] = text
        }
    }
}
