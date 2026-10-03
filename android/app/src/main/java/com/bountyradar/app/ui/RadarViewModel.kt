package com.bountyradar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.data.ProgramRepository
import com.bountyradar.app.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(val signedIn: Boolean, val email: String? = null)

data class PlatformStat(val platform: String, val count: Int, val paid: Int)

@OptIn(FlowPreview::class)
class RadarViewModel(app: Application) : AndroidViewModel(app) {

    private val auth: FirebaseAuth = Firebase.auth
    private val repo = ProgramRepository()
    private val settings = SettingsStore(app)

    private val started = SharingStarted.WhileSubscribed(5000)

    // ---- Auth ----
    private val _authState = MutableStateFlow(currentAuth())
    val authState: StateFlow<AuthState> = _authState

    // ---- Raw feed (live from Firestore, already parsed off the main thread) ----
    private val allPrograms: StateFlow<List<ProgramItem>> =
        repo.observePrograms()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, started, emptyList())

    // ---- Feed controls ----
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())
    val sort = MutableStateFlow(SortBy.NEWEST)

    /** Search + filter + sort, computed on a background thread; typing is debounced. */
    val programs: StateFlow<List<ProgramItem>> =
        combine(allPrograms, query.debounce(200), filters, sort) { list, q, f, s ->
            applyAll(list, q, f, s)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, started, emptyList())

    val totalCount: StateFlow<Int> =
        allPrograms.map { it.size }.stateIn(viewModelScope, started, 0)

    val newTodayCount: StateFlow<Int> =
        allPrograms.map { list -> list.count { it.isNew } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, started, 0)

    /** Per-platform breakdown for the Platforms tab. */
    val platformStats: StateFlow<List<PlatformStat>> =
        allPrograms.map { list ->
            list.groupBy { it.platformKey }
                .map { (p, items) -> PlatformStat(p, items.size, items.count { it.program.bounty }) }
                .sortedByDescending { it.count }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, started, emptyList())

    /** Distinct platform keys for the filter sheet. */
    val platformKeys: StateFlow<List<String>> =
        platformStats.map { stats -> stats.map { it.platform } }
            .stateIn(viewModelScope, started, emptyList())

    // ---- Bookmarks ----
    val bookmarks: StateFlow<Set<String>> =
        settings.bookmarks.stateIn(viewModelScope, started, emptySet())

    val savedPrograms: StateFlow<List<ProgramItem>> =
        combine(allPrograms, bookmarks) { list, marks -> list.filter { it.docId in marks } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, started, emptyList())

    fun toggleBookmark(docId: String) {
        viewModelScope.launch { settings.toggleBookmark(docId) }
    }

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

    fun programById(docId: String): ProgramItem? =
        allPrograms.value.firstOrNull { it.docId == docId }

    // ---- Account ----
    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await(); _authState.value = currentAuth()
    }
    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email.trim(), password).await(); _authState.value = currentAuth()
    }
    fun signOut() { auth.signOut(); _authState.value = currentAuth() }

    private fun currentAuth() = AuthState(auth.currentUser != null, auth.currentUser?.email)

    private fun applyAll(list: List<ProgramItem>, q: String, f: Filters, s: SortBy): List<ProgramItem> {
        val needle = q.trim().lowercase()
        val cutoff = f.recency.window?.let { System.currentTimeMillis() - it.toMillis() }
        val filtered = list.filter { item ->
            (f.platforms.isEmpty() || item.platformKey in f.platforms) &&
                when (f.reward) {
                    RewardFilter.PAID -> item.program.bounty
                    RewardFilter.VDP -> !item.program.bounty
                    RewardFilter.ANY -> true
                } &&
                (cutoff == null || item.firstSeenMillis >= cutoff) &&
                (!f.web3Only || item.isWeb3) &&
                (needle.isEmpty() || item.searchText.contains(needle))
        }
        return when (s) {
            SortBy.NEWEST -> filtered.sortedByDescending { it.firstSeenMillis }
            SortBy.REWARD_HIGH -> filtered.sortedByDescending { it.maxReward }
            SortBy.PLATFORM -> filtered.sortedBy { it.platformKey }
            SortBy.NAME -> filtered.sortedBy { it.nameLower }
        }
    }
}
