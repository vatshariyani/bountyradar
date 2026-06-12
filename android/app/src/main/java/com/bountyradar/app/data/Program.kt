package com.bountyradar.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Mirrors a document written by the Python poller (see poller/models.py).
 * Field names use @PropertyName because Firestore stores snake_case.
 */
data class Program(
    // @DocumentId injects the Firestore document id (our unique doc_id hash).
    // Without this, docId is blank for every row and the LazyColumn key collides.
    @DocumentId val docId: String = "",
    val platform: String = "",
    val name: String = "",
    val url: String = "",
    val bounty: Boolean = false,
    @get:PropertyName("reward_range") @set:PropertyName("reward_range")
    var rewardRange: String = "",
    val scope: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    @get:PropertyName("first_seen") @set:PropertyName("first_seen")
    var firstSeen: String = "",
)
