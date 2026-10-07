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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/** An API response as it was last fetched, by its path. */
@Entity(tableName = "responses")
data class SavedResponse(@PrimaryKey val path: String, val json: String, val savedAt: Long)

@Dao
interface SavedResponseDao {
    @Query("SELECT * FROM responses WHERE path = :path")
    fun observe(path: String): Flow<SavedResponse?>

    @Upsert
    suspend fun save(response: SavedResponse)

    @Query("DELETE FROM responses")
    suspend fun clear()
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
class Store(private val dao: SavedResponseDao, private val api: ApiClient) {

    /** The saved copy straight away, then the fresh one; later saves (see [save]) show up too. */
    fun <T> observe(path: String, serializer: KSerializer<T>): Flow<Loaded<T>> = channelFlow {
        val fetch = MutableStateFlow<FetchState>(FetchState.Running)
        launch {
            fetch.value = try {
                saveText(path, api.getText(path))
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
    suspend fun <T> save(path: String, value: T, serializer: KSerializer<T>) =
        saveText(path, api.json.encodeToString(serializer, value))

    suspend fun clear() = dao.clear()

    private suspend fun saveText(path: String, json: String) =
        dao.save(SavedResponse(path, json, System.currentTimeMillis()))

    private sealed interface FetchState {
        data object Running : FetchState
        data object Done : FetchState
        data class Failed(val error: ApiException) : FetchState
    }
}
