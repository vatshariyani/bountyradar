package com.bountyradar.app.data

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import org.json.JSONArray
import java.util.concurrent.Executors

/** kind: exploited | advisory | research | writeup | news */
@Immutable
data class NewsItem(
    val id: String,
    val kind: String,
    val source: String,
    val title: String,
    val summary: String,
    val url: String,
    val date: String,
    val severity: String,
    val tags: List<String>,
)

/**
 * The poller publishes the whole news feed as ONE document (a JSON string), so
 * opening the News tab costs a single Firestore read.
 */
class NewsRepository {
    private val doc = Firebase.firestore.collection("programs").document("_feed_news")
    private val executor = Executors.newSingleThreadExecutor()

    fun observeNews(): Flow<List<NewsItem>> = callbackFlow {
        val registration = doc.addSnapshotListener(executor) { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val items = try {
                parse(snapshot.getString("json").orEmpty())
            } catch (e: Exception) {
                android.util.Log.w("BountyRadar", "news parse failed", e)
                emptyList()
            }
            trySend(items)
        }
        awaitClose { registration.remove() }
    }.conflate()

    private fun parse(json: String): List<NewsItem> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tags = o.optJSONArray("tags")
            NewsItem(
                id = o.optString("id"),
                kind = o.optString("kind"),
                source = o.optString("src"),
                title = o.optString("title"),
                summary = o.optString("summary"),
                url = o.optString("url"),
                date = o.optString("date"),
                severity = o.optString("sev"),
                tags = if (tags == null) emptyList() else List(tags.length()) { tags.optString(it) },
            )
        }.distinctBy { it.id }
    }
}
