package com.bountyradar.app.data

import androidx.compose.runtime.Immutable
import java.time.Duration

/**
 * UI-ready wrapper around [Program]. Everything the list needs (badges, sort
 * keys, search text) is computed ONCE here, off the main thread, instead of
 * being re-parsed on every recomposition while scrolling. Marked @Immutable so
 * Compose can skip unchanged cards.
 */
@Immutable
class ProgramItem(val program: Program) {
    val docId: String = program.docId
    val platformKey: String = program.platform.removePrefix("fb:")
    val platformLabel: String = program.platformLabel()
    val firstSeenMillis: Long = program.firstSeenInstant()?.toEpochMilli() ?: 0L
    val isNew: Boolean = program.isNewWithin(Duration.ofDays(1))
    val isUpdated: Boolean = !isNew && program.isRecentlyUpdated()
    val maxReward: Long = program.maxReward()
    val isWeb3: Boolean = program.isWeb3()
    val scopeCount: Int = program.scope.size
    val nameLower: String = program.name.lowercase()

    /** Lower-cased haystack for search (scope capped so huge programs stay cheap). */
    val searchText: String = buildString {
        append(nameLower).append(' ').append(platformKey).append(' ')
        program.tags.forEach { append(it).append(' ') }
        program.scope.take(60).forEach { append(it).append(' ') }
    }.lowercase()
}
