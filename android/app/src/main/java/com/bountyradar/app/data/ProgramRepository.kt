package com.bountyradar.app.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads the `programs` collection. Uses a realtime snapshot listener so the list
 * updates live the instant the poller writes a new program — same trigger as the
 * push, so the app is already current when you tap the notification.
 */
class ProgramRepository {

    private val collection = Firebase.firestore.collection("programs")

    fun observePrograms(): Flow<List<Program>> = callbackFlow {
        val registration = collection
            .orderBy("first_seen", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Permission-denied (rules) or network errors must NOT crash the
                    // app — log and surface an empty list so the UI stays alive.
                    android.util.Log.w("BountyRadar", "Firestore listen failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = try {
                    snapshot?.toObjects<Program>() ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.w("BountyRadar", "Firestore deserialize failed", e)
                    emptyList()
                }
                trySend(items)
            }
        awaitClose { registration.remove() }
    }
}
