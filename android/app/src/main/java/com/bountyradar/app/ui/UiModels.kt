package com.bountyradar.app.ui

import java.time.Duration

/** How the feed is ordered. */
enum class SortBy(val label: String) {
    BEST("Best targets"),
    NEWEST("Newest first"),
    UPDATED("Recently updated"),
    REWARD_HIGH("Highest reward"),
    SCOPE("Largest scope"),
    PLATFORM("Platform A-Z"),
    NAME("Name A-Z"),
}

/** "New within" recency window. */
enum class Recency(val label: String, val window: Duration?) {
    ALL("Any time", null),
    DAY("New today", Duration.ofDays(1)),
    THREE_DAYS("Last 3 days", Duration.ofDays(3)),
    WEEK("Last 7 days", Duration.ofDays(7)),
    MONTH("Last 30 days", Duration.ofDays(30)),
}

/** Reward filter. */
enum class RewardFilter(val label: String) {
    ANY("Any"),
    PAID("Paid bounty"),
    VDP("VDP only"),
}

/** What kind of assets a program has in scope (derived from its scope list). */
enum class ScopeType(val label: String) {
    WILDCARD("Wildcard domains"),
    API("API"),
    MOBILE("Mobile app"),
}

/** Minimum top reward, in the program's own currency units. */
val MIN_REWARD_STEPS: List<Long> = listOf(0, 500, 1_000, 5_000, 10_000, 50_000)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** The complete filter state applied to the feed. */
data class Filters(
    val platforms: Set<String> = emptySet(),   // empty = all platforms
    val reward: RewardFilter = RewardFilter.ANY,
    val recency: Recency = Recency.ALL,
    val web3Only: Boolean = false,
    val minReward: Long = 0,
    val scopeTypes: Set<ScopeType> = emptySet(),
    val updatedOnly: Boolean = false,
) {
    val activeCount: Int
        get() = platforms.size + scopeTypes.size +
            (if (reward != RewardFilter.ANY) 1 else 0) +
            (if (recency != Recency.ALL) 1 else 0) +
            (if (web3Only) 1 else 0) +
            (if (minReward > 0) 1 else 0) +
            (if (updatedOnly) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}
