package com.bountyradar.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bountyradar.app.data.AccountsStore
import com.bountyradar.app.data.HackerOneData
import com.bountyradar.app.data.IntigritiData
import com.bountyradar.app.data.NewsItem
import com.bountyradar.app.data.NewsRepository
import com.bountyradar.app.data.PlatformApi
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.data.ProgramRepository
import com.bountyradar.app.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthState(val signedIn: Boolean, val email: String? = null)

data class PlatformStat(val platform: String, val count: Int, val paid: Int)

/** State of one connected platform account. */
data class AccountUi<T>(
    val connected: Boolean = false,
    val label: String = "",
    val loading: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class RadarViewModel(app: Application) : AndroidViewModel(app) {

    private val auth: FirebaseAuth = Firebase.auth
    private val repo = ProgramRepository()
    private val newsRepo = NewsRepository()
    private val settings = SettingsStore(app)
    private val accounts by lazy { AccountsStore(app) }

    private val started = SharingStarted.WhileSubscribed(5000)

    // ---- Auth ----
    private val _authState = MutableStateFlow(currentAuth())
    val authState: StateFlow<AuthState> = _authState

    // ---- Raw feed (live from Firestore, already parsed off the main thread) ----
    private val _feedLoaded = MutableStateFlow(false)
    /** False until the first snapshot arrives, so the feed can show skeletons. */
    val feedLoaded: StateFlow<Boolean> = _feedLoaded

    private val allPrograms: StateFlow<List<ProgramItem>> =
        repo.observePrograms()
            .catch { emit(emptyList()) }
            .onEach { _feedLoaded.value = true }
            .stateIn(viewModelScope, started, emptyList())

    // ---- Feed controls ----
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())
    val sort: StateFlow<SortBy> =
        settings.sortBy.stateIn(viewModelScope, SharingStarted.Eagerly, SortBy.BEST)

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

    // ---- "New since your last visit" ----
    private val visitBase = MutableStateFlow(System.currentTimeMillis() - 86_400_000L)
    init {
        viewModelScope.launch { visitBase.value = settings.beginVisit(System.currentTimeMillis()) }
    }
    val newSinceVisit: StateFlow<Int> =
        combine(allPrograms, visitBase) { list, base -> list.count { it.firstSeenMillis > base } }
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

    /** Distinct platform keys for the filter sheet and alert toggles. */
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

    // ---- Notes ----
    fun note(docId: String): Flow<String> = settings.note(docId)
    fun saveNote(docId: String, text: String) {
        viewModelScope.launch { settings.setNote(docId, text.trim()) }
    }

    // ---- News ----
    val newsKind = MutableStateFlow("all")
    private val allNews: StateFlow<List<NewsItem>> =
        newsRepo.observeNews().catch { emit(emptyList()) }
            .stateIn(viewModelScope, started, emptyList())
    val news: StateFlow<List<NewsItem>> =
        combine(allNews, newsKind) { list, kind ->
            if (kind == "all") list else list.filter { it.kind == kind }
        }.stateIn(viewModelScope, started, emptyList())

    // ---- Theme ----
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    // ---- Alerts per platform ----
    val mutedPlatforms: StateFlow<Set<String>> =
        settings.mutedPlatforms.stateIn(viewModelScope, started, emptySet())

    fun toggleMuted(platform: String) {
        viewModelScope.launch { settings.toggleMuted(platform) }
    }

    /**
     * Keep FCM topic subscriptions in line with the mute list. With nothing
     * muted we stay on the catch-all topic (so a brand-new platform still
     * alerts); once anything is muted we rely on per-platform topics only.
     */
    fun syncAlertTopics(platforms: List<String>, muted: Set<String>) {
        if (platforms.isEmpty()) return
        val fcm = Firebase.messaging
        if (muted.isEmpty()) fcm.subscribeToTopic(TOPIC_ALL) else fcm.unsubscribeFromTopic(TOPIC_ALL)
        platforms.forEach { p ->
            val topic = "plat_" + p.replace(Regex("[^a-zA-Z0-9_.~-]"), "_")
            if (p in muted) fcm.unsubscribeFromTopic(topic) else fcm.subscribeToTopic(topic)
        }
    }

    // ---- Filter / sort mutators ----
    fun togglePlatform(p: String) = filters.update { f ->
        f.copy(platforms = if (p in f.platforms) f.platforms - p else f.platforms + p)
    }
    fun toggleScopeType(t: ScopeType) = filters.update { f ->
        f.copy(scopeTypes = if (t in f.scopeTypes) f.scopeTypes - t else f.scopeTypes + t)
    }
    fun setReward(r: RewardFilter) = filters.update { it.copy(reward = r) }
    fun setRecency(r: Recency) = filters.update { it.copy(recency = r) }
    fun setWeb3Only(v: Boolean) = filters.update { it.copy(web3Only = v) }
    fun setMinReward(v: Long) = filters.update { it.copy(minReward = v) }
    fun setUpdatedOnly(v: Boolean) = filters.update { it.copy(updatedOnly = v) }
    fun clearFilters() { filters.value = Filters() }
    fun setSort(s: SortBy) { viewModelScope.launch { settings.setSortBy(s) } }

    fun programById(docId: String): ProgramItem? =
        allPrograms.value.firstOrNull { it.docId == docId }

    /** Live view of one program, so an open detail screen survives a cold feed. */
    fun programFlow(docId: String): Flow<ProgramItem?> =
        allPrograms.map { list -> list.firstOrNull { it.docId == docId } }

    fun newsById(id: String): NewsItem? = allNews.value.firstOrNull { it.id == id }

    // ---- Connected platform accounts (tokens stay encrypted on this device) ----
    private val _hackerOne = MutableStateFlow(AccountUi<HackerOneData>())
    val hackerOne: StateFlow<AccountUi<HackerOneData>> = _hackerOne
    private val _intigriti = MutableStateFlow(AccountUi<IntigritiData>())
    val intigriti: StateFlow<AccountUi<IntigritiData>> = _intigriti

    /** Called when the Accounts screen opens: restore saved connections and load. */
    fun loadAccounts() {
        // First touch opens the Keystore-backed store — keep it off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            if (accounts.hasHackerOne && !_hackerOne.value.connected) {
                _hackerOne.value = AccountUi(connected = true, label = accounts.h1Username)
                refreshHackerOne()
            }
            if (accounts.hasIntigriti && !_intigriti.value.connected) {
                _intigriti.value = AccountUi(connected = true, label = "Token saved")
                refreshIntigriti()
            }
        }
    }

    fun connectHackerOne(username: String, token: String) {
        accounts.saveHackerOne(username, token)
        _hackerOne.value = AccountUi(connected = true, label = username.trim())
        refreshHackerOne()
    }

    fun refreshHackerOne() {
        if (!accounts.hasHackerOne) return
        _hackerOne.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { PlatformApi.hackerOne(accounts.h1Username, accounts.h1Token) }
                .onSuccess { d -> _hackerOne.update { it.copy(loading = false, data = d) } }
                .onFailure { e -> _hackerOne.update { it.copy(loading = false, error = e.message ?: "Failed") } }
        }
    }

    fun disconnectHackerOne() {
        accounts.clearHackerOne(); _hackerOne.value = AccountUi()
    }

    fun connectIntigriti(token: String) {
        accounts.saveIntigriti(token)
        _intigriti.value = AccountUi(connected = true, label = "Token saved")
        refreshIntigriti()
    }

    fun refreshIntigriti() {
        if (!accounts.hasIntigriti) return
        _intigriti.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { PlatformApi.intigriti(accounts.intigritiToken) }
                .onSuccess { d -> _intigriti.update { it.copy(loading = false, data = d) } }
                .onFailure { e -> _intigriti.update { it.copy(loading = false, error = e.message ?: "Failed") } }
        }
    }

    fun disconnectIntigriti() {
        accounts.clearIntigriti(); _intigriti.value = AccountUi()
    }

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
                (f.minReward == 0L || item.maxReward >= f.minReward) &&
                (!f.updatedOnly || item.isUpdated) &&
                (ScopeType.WILDCARD !in f.scopeTypes || item.hasWildcard) &&
                (ScopeType.API !in f.scopeTypes || item.hasApi) &&
                (ScopeType.MOBILE !in f.scopeTypes || item.hasMobile) &&
                (needle.isEmpty() || item.searchText.contains(needle))
        }
        return when (s) {
            SortBy.BEST -> filtered.sortedByDescending { it.score }
            SortBy.NEWEST -> filtered.sortedByDescending { it.firstSeenMillis }
            SortBy.UPDATED -> filtered.sortedByDescending { it.updatedMillis }
            SortBy.REWARD_HIGH -> filtered.sortedByDescending { it.maxReward }
            SortBy.SCOPE -> filtered.sortedByDescending { it.scopeCount }
            SortBy.PLATFORM -> filtered.sortedBy { it.platformKey }
            SortBy.NAME -> filtered.sortedBy { it.nameLower }
        }
    }

    private companion object { const val TOPIC_ALL = "new_programs" }
}
