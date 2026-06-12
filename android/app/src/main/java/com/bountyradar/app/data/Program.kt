package com.bountyradar.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Mirrors a document written by the Python poller (see poller/models.py).
 * Field names use @PropertyName because Firestore stores snake_case.
 */
data class Program(
    // @DocumentId injects the Firestore document id (our unique doc_id hash).
    // Without this, docId is blank for every row and the LazyColumn key collides.
    @DocumentId val docId: String = "",
    val platform: String = "",
    val handle: String = "",
    val name: String = "",
    val url: String = "",
    val bounty: Boolean = false,
    @get:PropertyName("reward_range") @set:PropertyName("reward_range")
    var rewardRange: String = "",
    val scope: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @get:PropertyName("launched_at") @set:PropertyName("launched_at")
    var launchedAt: String = "",
    @get:PropertyName("first_seen") @set:PropertyName("first_seen")
    var firstSeen: String = "",
    @get:PropertyName("updated_at") @set:PropertyName("updated_at")
    var updatedAt: String = "",
) {
    /** Highest numeric value found in the reward string (for sorting). */
    fun maxReward(): Long =
        REWARD_RE.findAll(rewardRange)
            .map { it.value.replace(",", "").toLongOrNull() ?: 0L }
            .maxOrNull() ?: 0L

    fun firstSeenInstant(): Instant? = runCatching {
        OffsetDateTime.parse(firstSeen).toInstant()
    }.getOrNull()

    /** True if we first saw this program within [window] of now. */
    fun isNewWithin(window: Duration): Boolean {
        val seen = firstSeenInstant() ?: return false
        return Duration.between(seen, Instant.now()) <= window
    }

    private fun updatedAtInstant(): Instant? = runCatching {
        OffsetDateTime.parse(updatedAt).toInstant()
    }.getOrNull()

    /** Old program that got a recent scope/reward change (not a brand-new one). */
    fun isRecentlyUpdated(): Boolean {
        val u = updatedAtInstant() ?: return false
        val within = Duration.between(u, Instant.now()) <= Duration.ofDays(2)
        return within && !isNewWithin(Duration.ofDays(2))
    }

    fun isWeb3(): Boolean = tags.any { it.equals("web3", true) } || platform in WEB3_PLATFORMS

    /** A short, human label for the platform (handles the "fb:" aggregator prefix). */
    fun platformLabel(): String =
        platform.removePrefix("fb:").replaceFirstChar { it.uppercase() }

    companion object {
        private val REWARD_RE = Regex("""\d[\d,]*""")
        val WEB3_PLATFORMS = setOf("immunefi", "sherlock", "cantina")
    }
}
