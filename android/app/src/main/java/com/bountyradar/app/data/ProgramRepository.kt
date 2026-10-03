package com.bountyradar.app.data

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.Executors

/**
 * Reads the `programs` collection with a realtime snapshot listener.
 *
 * The listener runs on a background executor: deserializing ~1,200 documents
 * (some with thousands of scope entries) on the main thread froze scrolling
 * every time a snapshot arrived.
 */
class ProgramRepository {

    private val collection = Firebase.firestore.collection("programs")
    private val executor = Executors.newSingleThreadExecutor()

    fun observePrograms(): Flow<List<ProgramItem>> = callbackFlow {
        val registration = collection
            .orderBy("first_seen", Query.Direction.DESCENDING)
            .limit(5000)   // show ALL programs; filter/sort happen client-side
            .addSnapshotListener(executor) { snapshot, error ->
                if (error != null) {
                    // Permission-denied (rules) or network errors must NOT crash the
                    // app — log and surface an empty list so the UI stays alive.
                    android.util.Log.w("BountyRadar", "Firestore listen failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = try {
                    snapshot?.toObjects<Program>()?.map(::ProgramItem) ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.w("BountyRadar", "Firestore deserialize failed", e)
                    emptyList()
                }
                trySend(items)
            }
        awaitClose { registration.remove() }
    }.conflate()
}
