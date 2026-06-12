package com.bountyradar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bountyradar.app.data.Program
import com.bountyradar.app.data.ProgramRepository
import com.bountyradar.app.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Duration

data class AuthState(val signedIn: Boolean, val email: String? = null)

data class PlatformStat(val platform: String, val count: Int, val paid: Int)

class RadarViewModel(app: Application) : AndroidViewModel(app) {

    private val auth: FirebaseAuth = Firebase.auth
    private val repo = ProgramRepository()
    private val settings = SettingsStore(app)

    private val started = SharingStarted.WhileSubscribed(5000)

    // ---- Auth ----
    private val _authState = MutableStateFlow(currentAuth())
    val authState: StateFlow<AuthState> = _authState

    // ---- Raw feed (live from Firestore) ----
    private val allPrograms: StateFlow<List<Program>> =
        repo.observePrograms()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, started, emptyList())

    // ---- Feed controls ----
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())
    val sort = MutableStateFlow(SortBy.NEWEST)

    /** Search + filter + sort applied — what the feed shows. */
    val programs: StateFlow<List<Program>> =
        combine(allPrograms, query, filters, sort) { list, q, f, s ->
            applyAll(list, q, f, s)
        }.stateIn(viewModelScope, started, emptyList())

    val totalCount: StateFlow<Int> =
        allPrograms.map { it.size }.stateIn(viewModelScope, started, 0)

    val newTodayCount: StateFlow<Int> =
        allPrograms.map { list -> list.count { it.isNewWithin(Duration.ofDays(1)) } }
            .stateIn(viewModelScope, started, 0)

    /** Per-platform breakdown for the Platforms tab. */
    val platformStats: StateFlow<List<PlatformStat>> =
        allPrograms.map { list ->
            list.groupBy { it.platform.removePrefix("fb:") }
                .map { (p, items) -> PlatformStat(p, items.size, items.count { it.bounty }) }
                .sortedByDescending { it.count }
        }.stateIn(viewModelScope, started, emptyList())

    /** Distinct platform keys for the filter sheet. */
    val platformKeys: StateFlow<List<String>> =
        platformStats.map { stats -> stats.map { it.platform } }
            .stateIn(viewModelScope, started, emptyList())

    // ---- Bookmarks ----
    val bookmarks: StateFlow<Set<String>> =
        settings.bookmarks.stateIn(viewModelScope, started, emptySet())

    val savedPrograms: StateFlow<List<Program>> =
        combine(allPrograms, bookmarks) { list, marks -> list.filter { it.docId in marks } }
            .stateIn(viewModelScope, started, emptyList())

    fun toggleBookmark(docId: String) = viewModelScope.launch { settings.toggleBookmark(docId) }

    // ---- Theme ----
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    // ---- Filter mutators ----
    fun togglePlatform(p: String) = filters.update { f ->
        f.copy(platforms = if (p in f.platforms) f.platforms - p else f.platforms + p)
    }
    fun setReward(r: RewardFilter) = filters.update { it.copy(reward = r) }
    fun setRecency(r: Recency) = filters.update { it.copy(recency = r) }
    fun setWeb3Only(v: Boolean) = filters.update { it.copy(web3Only = v) }
    fun clearFilters() { filters.value = Filters() }
    fun setSort(s: SortBy) { sort.value = s }

    fun programById(docId: String): Program? = allPrograms.value.firstOrNull { it.docId == docId }

    // ---- Account ----
    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await(); _authState.value = currentAuth()
    }
    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email.trim(), password).await(); _authState.value = currentAuth()
    }
    fun signOut() { auth.signOut(); _authState.value = currentAuth() }

    private fun currentAuth() = AuthState(auth.currentUser != null, auth.currentUser?.email)

    private fun applyAll(list: List<Program>, q: String, f: Filters, s: SortBy): List<Program> {
        var r = list
        if (f.platforms.isNotEmpty()) r = r.filter { it.platform.removePrefix("fb:") in f.platforms }
        when (f.reward) {
            RewardFilter.PAID -> r = r.filter { it.bounty }
            RewardFilter.VDP -> r = r.filter { !it.bounty }
            RewardFilter.ANY -> {}
        }
        f.recency.window?.let { w -> r = r.filter { it.isNewWithin(w) } }
        if (f.web3Only) r = r.filter { it.isWeb3() }
        if (q.isNotBlank()) r = r.filter { p ->
            p.name.contains(q, true) || p.platform.contains(q, true) ||
                p.tags.any { it.contains(q, true) } || p.scope.any { it.contains(q, true) }
        }
        return when (s) {
            SortBy.NEWEST -> r.sortedByDescending { it.firstSeen }
            SortBy.REWARD_HIGH -> r.sortedByDescending { it.maxReward() }
            SortBy.PLATFORM -> r.sortedBy { it.platform }
            SortBy.NAME -> r.sortedBy { it.name.lowercase() }
        }
    }
}
