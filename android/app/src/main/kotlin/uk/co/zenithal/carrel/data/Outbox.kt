package uk.co.zenithal.carrel.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import uk.co.zenithal.carrel.CarrelApp
import uk.co.zenithal.carrel.auth.Session
import java.time.Instant

/**
 * A library change made without a signal, waiting to be sent: a save (`request`, as JSON, with its changedAt) or, with
 * no request, removing the book. Kept in the order made.
 */
@Entity(tableName = "changes")
data class WaitingChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Whose change it is, so it's never sent as anyone else. */
    val userId: String,
    val bookId: Long,
    val request: String?,
    /** When it was made, for a removal (a save has it in its request). */
    val changedAt: String,
)

@Dao
interface WaitingChangeDao {
    @Insert
    suspend fun add(change: WaitingChange)

    @Query("SELECT * FROM changes ORDER BY id LIMIT 1")
    suspend fun next(): WaitingChange?

    @Query("SELECT bookId FROM changes")
    fun observeBooks(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM changes WHERE bookId = :bookId")
    suspend fun countFor(bookId: Long): Int

    @Query("DELETE FROM changes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM changes")
    suspend fun clear()
}

/**
 * The reader's own changes, unlike carrel.db (copies of API responses, started again on any new version), so a new
 * version of this needs a real migration.
 */
@Database(entities = [WaitingChange::class], version = 1, exportSchema = false)
abstract class ChangesDatabase : RoomDatabase() {
    abstract fun changes(): WaitingChangeDao

    companion object {
        fun create(context: Context): ChangesDatabase =
            Room.databaseBuilder(context, ChangesDatabase::class.java, "changes.db").build()
    }
}

/**
 * Library changes made without a signal, sent in order by [SyncWorker] once there is one, even with the app closed.
 * A change the API turns away because the book was changed or removed more recently elsewhere is dropped, as the
 * newer one wins.
 */
class Outbox(private val context: Context, private val dao: WaitingChangeDao, private val currentUser: () -> String?) {
    /** The books with changes waiting, once for each change. */
    val waitingBooks: Flow<List<Long>> = dao.observeBooks()

    suspend fun isWaiting(bookId: Long) = dao.countFor(bookId) > 0

    /** Keeps a change to send: a save, or a removal when `request` is null. */
    suspend fun add(bookId: Long, request: SaveEntryRequest?) {
        val userId = currentUser() ?: throw ApiException(401, "Your session has expired. Sign in again.")
        val json = request?.let { CarrelJson.encodeToString(SaveEntryRequest.serializer(), it) }
        dao.add(WaitingChange(userId = userId, bookId = bookId, request = json, changedAt = request?.changedAt ?: Instant.now().toString()))
        schedule()
    }

    /** Drops every waiting change, when the reader signs out or changes. */
    suspend fun clear() = dao.clear()

    /** Makes sure waiting changes will be sent, e.g. after the app was force-stopped, which cancels the work. */
    suspend fun scheduleIfWaiting() {
        if (dao.next() != null) schedule()
    }

    /** Sends the waiting changes as soon as there's a signal; one more run after any already under way. */
    fun schedule() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Sends the waiting changes in order. True when they've all gone (or been dropped); false to try again later,
     * without a signal, a session, or a working API.
     */
    suspend fun send(api: ApiClient, store: Store): Boolean {
        var sent = false
        while (true) {
            val change = dao.next() ?: break
            if (change.userId != currentUser()) {
                // Another reader's, which the store's clearing missed; never send it as this one.
                dao.delete(change.id)
                continue
            }
            try {
                if (change.request != null) {
                    val request = CarrelJson.decodeFromString(SaveEntryRequest.serializer(), change.request)
                    api.send(HttpMethod.Put, "/library/books/${change.bookId}", request, SaveEntryRequest.serializer(), LibraryEntry.serializer())
                } else {
                    api.delete("/library/books/${change.bookId}?changedAt=${change.changedAt.encodeURLParameter()}")
                }
            } catch (e: ApiException) {
                // Turned away for good: changed or removed more recently elsewhere (409), or no longer valid (400, 404).
                if (e.status !in setOf(400, 404, 409)) return false
            }
            dao.delete(change.id)
            sent = true
        }
        // What the API now has, including any newer changes that won and the API's own read dates and progress.
        if (sent) {
            runCatching { store.save(LIBRARY_PATH, api.get(LIBRARY_PATH, LibrarySerializer), LibrarySerializer) }
        }
        return true
    }

    private companion object {
        const val SYNC_WORK = "send-library-changes"
    }
}

/** Sends the waiting library changes (see Outbox), run by WorkManager once there's a signal. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CarrelApp).container
        // The saved session loads after the app starts; until it has, there's no token to send changes with.
        val session = container.session.state.first { it != Session.Loading }
        if (session !is Session.SignedIn) return Result.success()
        return if (container.outbox.send(container.api, container.store)) Result.success() else Result.retry()
    }
}

/** Whether the phone has a connection to the internet, as Android sees it. */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(isOnline())
    val online: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    }

    private fun isOnline() =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
