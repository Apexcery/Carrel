package uk.co.zenithal.carrel.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Recent searches, kept on this device only and separately for each signed-in reader (and for signed-out browsing),
 * as the website keeps them in the browser.
 */
class RecentSearches(private val prefs: SharedPreferences) {
    private val serializer = ListSerializer(String.serializer())

    fun get(userId: String): List<String> =
        prefs.getString(userId, null)?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Moves the search to the top of the list (ignoring case), keeping the most recent few. */
    fun add(userId: String, query: String) {
        val recent = listOf(query) + get(userId).filterNot { it.equals(query, ignoreCase = true) }
        prefs.edit { putString(userId, Json.encodeToString(serializer, recent.take(MAX_RECENT))) }
    }

    fun clear(userId: String) = prefs.edit { remove(userId) }

    private companion object {
        const val MAX_RECENT = 8
    }
}
