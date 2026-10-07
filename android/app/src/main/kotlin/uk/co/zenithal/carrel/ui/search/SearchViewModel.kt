package uk.co.zenithal.carrel.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.data.ApiClient
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookSearchResponse
import uk.co.zenithal.carrel.data.BookSearchResult
import uk.co.zenithal.carrel.data.RecentSearches

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
    /** How many books match, once the first page has loaded. */
    var found by mutableStateOf<Int?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var page = 0
    private var hasMore = false
    private var job: Job? = null
    /** Bumped by each new search, so a cancelled one's late finish can't touch the new one's state. */
    private var generation = 0

    /** Searches for `text` and adds it to the reader's recent searches (`userId` is "" when signed out). */
    fun search(text: String, userId: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        recent.add(userId, q)
        job?.cancel()
        generation++
        loading = false
        query = q
        results = emptyList()
        found = null
        page = 0
        hasMore = true
        error = null
        loadMore()
    }

    /** Goes back to no search, e.g. once the search box is emptied. */
    fun clear() {
        job?.cancel()
        generation++
        query = null
        results = emptyList()
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

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_PAGE = 50
    }
}
