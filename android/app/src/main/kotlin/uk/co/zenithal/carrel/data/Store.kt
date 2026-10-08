package uk.co.zenithal.carrel.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap

/** An API response as it was last fetched, by its path. */
@Entity(tableName = "responses")
data class SavedResponse(@PrimaryKey val path: String, val json: String, val savedAt: Long)

@Dao
interface SavedResponseDao {
    @Query("SELECT * FROM responses WHERE path = :path")
    fun observe(path: String): Flow<SavedResponse?>

    @Query("SELECT * FROM responses WHERE path = :path")
    suspend fun get(path: String): SavedResponse?

    @Upsert
    suspend fun save(response: SavedResponse)

    @Query("DELETE FROM responses")
    suspend fun clear()

    @Query("UPDATE responses SET savedAt = 0")
    suspend fun markStale()
}

@Database(entities = [SavedResponse::class], version = 1, exportSchema = false)
abstract class CarrelDatabase : RoomDatabase() {
    abstract fun responses(): SavedResponseDao

    companion object {
        fun create(context: Context): CarrelDatabase =
            Room.databaseBuilder(context, CarrelDatabase::class.java, "carrel.db")
                // It only holds copies of API responses, so a new version can start again.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

/**
 * Something loaded from the API: the saved copy (or the fresh one, once it arrives), whether a fetch is under way,
 * and why the last fetch failed. A screen shows `data` whenever there is any, even while refreshing or after an error.
 */
data class Loaded<T>(val data: T?, val refreshing: Boolean, val error: ApiException?)

/**
 * API responses kept on the device, so screens open with what was there last time while a fresh copy loads, and
 * still show it with no signal. Like the website's query cache, it's cleared whenever the signed-in reader changes.
 */
class Store(private val dao: SavedResponseDao, private val api: ApiClient, private val scope: CoroutineScope) {
    /** How many times each path has been saved by the app itself (see [save]). */
    private val changes = ConcurrentHashMap<String, Int>()
    /** Fetches under way, by path, so screens asking for the same thing at once share one. */
    private val fetches = ConcurrentHashMap<String, Deferred<Unit>>()

    /** The saved copy straight away, then the fresh one; later saves (see [save]) show up too. */
    fun <T> observe(path: String, serializer: KSerializer<T>): Flow<Loaded<T>> = channelFlow {
        val fetch = MutableStateFlow<FetchState>(FetchState.Running)
        launch {
            fetch.value = try {
                // Moving between screens that use the same thing (the Library tab, a shelf, a book) needn't fetch it
                // each time. The app's own changes are saved as they're made, so only changes made elsewhere wait.
                val saved = dao.get(path)
                if (saved == null || System.currentTimeMillis() - saved.savedAt > FRESH_FOR_MS) refresh(path).await()
                FetchState.Done
            } catch (e: ApiException) {
                FetchState.Failed(e)
            }
        }
        combine(dao.observe(path), fetch) { saved, state ->
            Loaded(
                data = saved?.let { runCatching { api.json.decodeFromString(serializer, it.json) }.getOrNull() },
                refreshing = state == FetchState.Running,
                error = (state as? FetchState.Failed)?.error,
            )
        }.collectLatest { send(it) }
    }

    /** Replaces the saved copy, e.g. with what the API returned after a change, as the website's setQueryData does. */
    suspend fun <T> save(path: String, value: T, serializer: KSerializer<T>) {
        changes.merge(path, 1, Int::plus)
        saveText(path, api.json.encodeToString(serializer, value))
    }

    /** Changes the saved copy, if there is one, e.g. to put a library change into the saved library. */
    suspend fun <T> update(path: String, serializer: KSerializer<T>, change: (T) -> T) {
        val saved = dao.get(path) ?: return
        val value = runCatching { api.json.decodeFromString(serializer, saved.json) }.getOrNull() ?: return
        save(path, change(value), serializer)
    }

    suspend fun clear() {
        // Anything still on its way belongs to the reader whose data this was.
        fetches.values.forEach { it.cancel() }
        dao.clear()
    }

    /**
     * Keeps every saved copy but has each fetched again when next shown, e.g. after an import or emptying the library,
     * which change much of what's saved. (Clearing them would leave the screens showing them, such as the profile, empty.)
     */
    suspend fun markStale() = dao.markStale()

    /**
     * Fetches a fresh copy and saves it. Screens opened together (a book's panel and its suggestions both use the
     * library) share one request rather than each making their own.
     */
    private fun refresh(path: String): Deferred<Unit> {
        fetches[path]?.let { return it }
        val changesBefore = changes[path]
        val fetch = scope.async {
            val text = api.getText(path)
            // A change saved while this was on its way is newer than what the API sent.
            if (changes[path] == changesBefore) saveText(path, text)
        }
        fetches[path] = fetch
        // Also when cancelled before it starts.
        fetch.invokeOnCompletion { fetches.remove(path, fetch) }
        return fetch
    }

    private suspend fun saveText(path: String, json: String) =
        dao.save(SavedResponse(path, json, System.currentTimeMillis()))

    private sealed interface FetchState {
        data object Running : FetchState
        data object Done : FetchState
        data class Failed(val error: ApiException) : FetchState
    }

    private companion object {
        /** How long a saved copy counts as fresh, so opening another screen that uses it doesn't fetch it again. */
        const val FRESH_FOR_MS = 30_000L
    }
}
