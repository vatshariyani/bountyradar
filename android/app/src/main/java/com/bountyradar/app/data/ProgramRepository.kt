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
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.toObjects<Program>())
            }
        awaitClose { registration.remove() }
    }
}
