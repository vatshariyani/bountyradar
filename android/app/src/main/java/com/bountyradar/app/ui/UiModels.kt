package com.bountyradar.app.ui

import java.time.Duration

/** How the feed is ordered. */
enum class SortBy(val label: String) {
    NEWEST("Newest first"),
    REWARD_HIGH("Highest reward"),
    PLATFORM("Platform A–Z"),
    NAME("Name A–Z"),
}

/** "New within" recency window. */
enum class Recency(val label: String, val window: Duration?) {
    ALL("Any time", null),
    DAY("New today", Duration.ofDays(1)),
    THREE_DAYS("Last 3 days", Duration.ofDays(3)),
    WEEK("Last 7 days", Duration.ofDays(7)),
}

/** Reward filter. */
enum class RewardFilter(val label: String) {
    ANY("Any"),
    PAID("Paid bounty"),
    VDP("VDP only"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** The complete filter state applied to the feed. */
data class Filters(
    val platforms: Set<String> = emptySet(),   // empty = all platforms
    val reward: RewardFilter = RewardFilter.ANY,
    val recency: Recency = Recency.ALL,
    val web3Only: Boolean = false,
) {
    val activeCount: Int
        get() = platforms.size +
            (if (reward != RewardFilter.ANY) 1 else 0) +
            (if (recency != Recency.ALL) 1 else 0) +
            (if (web3Only) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}
