package com.bountyradar.app.data

import androidx.compose.runtime.Immutable
import com.bountyradar.app.ui.theme.platformName
import java.time.Duration
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.min

/**
 * UI-ready wrapper around [Program]. Everything the list needs (badges, sort
 * keys, search text, ranking score) is computed ONCE here, off the main thread,
 * instead of being re-parsed on every recomposition while scrolling. Marked
 * @Immutable so Compose can skip unchanged cards.
 */
@Immutable
class ProgramItem(val program: Program) {
    val docId: String = program.docId
    val platformKey: String = program.platform.removePrefix("fb:")
    val platformLabel: String = program.platformLabel()
    val firstSeenMillis: Long = program.firstSeenInstant()?.toEpochMilli() ?: 0L
    val updatedMillis: Long = program.updatedAtInstant()?.toEpochMilli() ?: firstSeenMillis
    val isNew: Boolean = program.isNewWithin(Duration.ofDays(1))
    val isUpdated: Boolean = !isNew && program.isRecentlyUpdated()
    val maxReward: Long = program.maxReward()
    val isWeb3: Boolean = program.isWeb3()
    val scopeCount: Int = program.scope.size
    val nameLower: String = program.name.lowercase()

    /** Reward line for the card: plain hyphens, sentence case, never blank. */
    val rewardLabel: String = when {
        !program.bounty -> "No bounty (VDP)"
        program.rewardRange.isBlank() -> "Paid bounty"
        else -> program.rewardRange.replace("–", " - ").replace("—", " - ")
            .replaceFirstChar { it.uppercase() }
    }

    /** "HackerOne · 19 assets" */
    val subtitle: String = platformName(platformKey) +
        if (scopeCount > 0) " · $scopeCount ${if (scopeCount == 1) "asset" else "assets"}" else ""

    // ---- scope shape ----
    val wildcardCount: Int
    val hasMobile: Boolean
    val hasApi: Boolean

    init {
        var wild = 0
        var mobile = false
        var api = false
        for (raw in program.scope) {
            val s = raw.lowercase()
            if ('*' in s) wild++
            if (!mobile && (
                    "play.google.com" in s || "apps.apple.com" in s || "itunes.apple.com" in s ||
                        s.endsWith(".apk") || s.endsWith(".ipa") || MOBILE_PKG.matches(s))
            ) mobile = true
            if (!api && ("api" in s || "graphql" in s)) api = true
        }
        wildcardCount = wild
        hasMobile = mobile
        hasApi = api
    }

    val hasWildcard: Boolean get() = wildcardCount > 0

    /**
     * "Best targets" ranking, 0–100. Favours what makes a target worth opening
     * the laptop for: it is fresh (few people have looked yet), it pays, and it
     * has a wide attack surface — wildcards above all.
     */
    val score: Float = run {
        val ageDays = if (firstSeenMillis == 0L) 365.0
        else (System.currentTimeMillis() - firstSeenMillis) / 86_400_000.0
        val fresh = 45.0 * exp(-ageDays.coerceAtLeast(0.0) / 5.0)
        val updated = if (isUpdated) 12.0 else 0.0
        val paid = if (program.bounty) 8.0 else 0.0
        val reward = min(25.0, 5.0 * log10(maxReward + 1.0))
        val surface = min(12.0, 4.0 * log10(scopeCount + 1.0))
        val wild = min(10.0, 3.0 * wildcardCount)
        (fresh + updated + paid + reward + surface + wild).toFloat()
    }

    /** Lower-cased haystack for search (scope capped so huge programs stay cheap). */
    val searchText: String = buildString {
        append(nameLower).append(' ').append(platformKey).append(' ')
        program.tags.forEach { append(it).append(' ') }
        program.scope.take(60).forEach { append(it).append(' ') }
    }.lowercase()

    private companion object {
        val MOBILE_PKG = Regex("""^(com|org|net|io)\.[a-z0-9_]+(\.[a-z0-9_]+)+$""")
    }
}
