package uk.co.zenithal.carrel.ui.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import uk.co.zenithal.carrel.data.ApiClient
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.AuthorSearchResult
import uk.co.zenithal.carrel.data.BookSearchResponse
import uk.co.zenithal.carrel.data.BookSearchResult
import uk.co.zenithal.carrel.data.RecentSearches
import uk.co.zenithal.carrel.data.validIsbn

/**
 * A search and its results, loaded 20 at a time as the reader scrolls (up to the API's 50 pages). Kept while the
 * reader opens a result and comes back.
 */
class SearchViewModel(private val api: ApiClient, private val recent: RecentSearches) : ViewModel() {
    /** The search shown, or null before the first one. */
    var query by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<BookSearchResult>>(emptyList())
        private set
    /** Authors whose names match, shown above the books; none if they fail to load. */
    var authors by mutableStateOf<List<AuthorSearchResult>>(emptyList())
        private set
    /** How many books match, once the first page has loaded. */
    var found by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** A scanned book's ISBN, to look up, until `onScanHandled`. */
    var scannedIsbn by mutableStateOf<String?>(null)
        private set
    /** Why the last scan found nothing to look up. */
    var scanMessage by mutableStateOf<String?>(null)
        private set
    private var page = 0
    private var hasMore = false
    private var job: Job? = null
    private var authorsJob: Job? = null
    /** Bumped by each new search, so a cancelled one's late finish can't touch the new one's state. */
    private var generation = 0

    /** Searches for `text` and adds it to the reader's recent searches (`userId` is "" when signed out). */
    fun search(text: String, userId: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        recent.add(userId, q)
        scanMessage = null
        job?.cancel()
        authorsJob?.cancel()
        generation++
        loading = false
        query = q
        results = emptyList()
        authors = emptyList()
        found = null
        page = 0
        hasMore = true
        error = null
        loadMore()
        loadAuthors(q)
    }

    /** Goes back to no search, e.g. once the search box is emptied. */
    fun clear() {
        job?.cancel()
        authorsJob?.cancel()
        generation++
        query = null
        results = emptyList()
        authors = emptyList()
        found = null
        loading = false
        error = null
    }

    fun loadMore() {
        val q = query ?: return
        if (loading || !hasMore) return
        loading = true
        error = null
        val mine = generation
        job = viewModelScope.launch {
            try {
                val next = page + 1
                val response = api.get("/books/search?q=${q.encodeURLParameter()}&page=$next", BookSearchResponse.serializer())
                if (mine != generation) return@launch
                page = next
                results = results + response.results
                if (found == null) found = response.found
                hasMore = response.results.size == PAGE_SIZE && results.size < response.found && next < MAX_PAGE
            } catch (e: ApiException) {
                if (mine == generation) error = e.message
            } finally {
                if (mine == generation) loading = false
            }
        }
    }

    /**
     * Scans a book's barcode. Google Play services shows the camera, so Carrel needs no camera permission of its own.
     * The result comes here, not to the screen, since the scanner (portrait only) may recreate a sideways activity.
     */
    fun scan(context: Context) {
        scanMessage = null
        GmsBarcodeScanning.getClient(context, SCAN_OPTIONS).startScan()
            .addOnSuccessListener { barcode ->
                // Every EAN-13 passes the ISBN-13 check, so only Bookland's 978 and 979 are books.
                val isbn = barcode.rawValue?.takeIf { it.startsWith("978") || it.startsWith("979") }?.let(::validIsbn)
                if (isbn != null) scannedIsbn = isbn else scanMessage = "That isn’t a book’s barcode. Try the one with the ISBN above it."
            }
            .addOnFailureListener { scanMessage = scanFailure(it) }
    }

    fun onScanHandled() {
        scannedIsbn = null
    }

    private fun loadAuthors(q: String) {
        val mine = generation
        authorsJob = viewModelScope.launch {
            try {
                val found = api.get("/authors/search?q=${q.encodeURLParameter()}", ListSerializer(AuthorSearchResult.serializer()))
                if (mine == generation) authors = found
            } catch (_: ApiException) {
                // The books still show.
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_PAGE = 50
    }
}

/** Books' barcodes only: an ISBN is printed as an EAN-13. */
private val SCAN_OPTIONS = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build()

/** Why the scanner failed, for the reader; null when they closed it, or one is already open. */
private fun scanFailure(e: Exception): String? = when ((e as? MlKitException)?.errorCode) {
    MlKitException.CODE_SCANNER_CANCELLED, MlKitException.CANCELLED, MlKitException.CODE_SCANNER_TASK_IN_PROGRESS -> null
    // The first scan starts Google Play services downloading the scanner.
    MlKitException.CODE_SCANNER_UNAVAILABLE, MlKitException.UNAVAILABLE -> "The scanner is still downloading. Try again in a minute."
    MlKitException.CODE_SCANNER_CAMERA_PERMISSION_NOT_GRANTED -> "Google Play services needs the camera to scan. Allow it in the phone’s settings."
    MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD -> "Update Google Play services to scan barcodes."
    else -> "The scanner couldn’t start. Type the ISBN instead."
}
