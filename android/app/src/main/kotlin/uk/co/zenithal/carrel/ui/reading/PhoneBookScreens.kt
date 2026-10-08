package uk.co.zenithal.carrel.ui.reading

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.BookSearchResponse
import uk.co.zenithal.carrel.data.BookSearchResult
import uk.co.zenithal.carrel.data.PhoneBook
import uk.co.zenithal.carrel.data.PhoneBookException
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.reader.openReader
import uk.co.zenithal.carrel.ui.theme.Carrel
import androidx.compose.ui.platform.LocalContext

/** Search results offered for linking a book. */
private const val LINK_RESULTS = 10

/** Copies a picked or shared EPUB into the app (see PhoneBooks.add), then hands it on (`onAdded`). */
@Composable
fun AddBookScreen(uri: String, onAdded: (PhoneBook) -> Unit, onBack: () -> Unit) {
    val container = LocalContainer.current
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        try {
            onAdded(container.phoneBooks.add(Uri.parse(uri)))
        } catch (e: PhoneBookException) {
            error = e.message
        }
    }
    Column(Modifier.fillMaxSize().background(Carrel.colors.paper).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Add a book")
        val problem = error
        if (problem == null) {
            Text("Adding it to Carrel…", style = Carrel.type.displayMedium, color = Carrel.colors.ink)
        } else {
            Text("Couldn’t add it", style = Carrel.type.displayMedium, color = Carrel.colors.ink)
            FormMessage(problem, Tone.Error)
            LinkButton("Back", onBack)
        }
    }
}

/**
 * Links a book on the phone to its book in Carrel, so reading it updates the library: first the book with one of the
 * file's ISBNs, if Carrel has one, to confirm; otherwise (or if that's not it) a search by its title and author.
 */
@Composable
fun LinkBookScreen(phoneBookId: Long, readAfter: Boolean, onLinked: (bookId: Long) -> Unit, onSkip: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = produceState<PhoneBook?>(null, phoneBookId) { value = container.phoneBooks.get(phoneBookId) }.value

    // A book just downloaded opens to read, once it's linked or not.
    fun thenRead(done: () -> Unit) {
        if (!readAfter) return done()
        scope.launch {
            container.phoneBooks.get(phoneBookId)?.let {
                try {
                    openReader(context, container, it)
                } catch (_: PhoneBookException) {
                    // Opened from the dashboard instead, which says what's wrong.
                }
            }
            done()
        }
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun link(path: String) = scope.launch {
        linking = true
        error = null
        try {
            // Opening the book stores it on the server, which a library entry needs.
            val detail = container.api.get(path, BookDetail.serializer())
            container.phoneBooks.link(phoneBookId, detail)
            thenRead { onLinked(detail.id) }
        } catch (e: ApiException) {
            error = e.message
        } finally {
            linking = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.paper).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (book == null) return@Column
        Column {
            Kicker("Link to a book in Carrel")
            Text(book.title, style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
            if (book.authorList.isNotEmpty()) Text("by ${listNames(book.authorList)}", style = Carrel.type.body, color = colors.inkSoft)
            Text(
                "Once it’s linked, reading it moves it to Reading in your library and keeps your progress up to date.",
                style = Carrel.type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 12.dp),
            )
            // Above the search results, so skipping doesn't mean scrolling past them.
            LinkButton(if (readAfter) "Skip and read" else "Skip for now", { thenRead(onSkip) }, Modifier.padding(top = 12.dp))
        }
        if (!searching && book.isbnList.isNotEmpty()) {
            IsbnMatch(book, linking, { link(it) }) { searching = true }
        } else {
            LinkSearch(book, linking) { link(it) }
        }
        error?.let { FormMessage(it, Tone.Error) }
    }
}

/** The book in Carrel with one of the file's ISBNs, to confirm; straight on to searching if there isn't one. */
@Composable
private fun IsbnMatch(book: PhoneBook, linking: Boolean, onConfirm: (path: String) -> Unit, onSearch: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    var attempt by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<BookDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt) {
        error = null
        try {
            found = book.isbnList.firstNotNullOfOrNull { container.api.getOrNull("/books/isbn/$it", BookDetail.serializer()) }
            if (found == null) onSearch()
        } catch (e: ApiException) {
            error = e.message
        }
    }
    val match = found
    when {
        error != null -> ErrorNotice(error.orEmpty(), { attempt++ })
        match == null -> Text("Looking for it in Carrel…", style = Carrel.type.mono, color = colors.inkSoft)
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle("Is this the book?")
            val authors = match.authors.filter { it.role == "author" }.map { it.name }
            BookRow(match.coverUrl, match.title, authors, match.firstPublishedYear)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PrimaryButton(if (linking) "Linking…" else "Yes, link it", { onConfirm("/books/${match.id}") }, enabled = !linking)
                LinkButton("No, search instead", onSearch, color = colors.ink)
            }
        }
    }
}

/** A search for the book, starting with its title and first author; tapping a result links it. */
@Composable
private fun LinkSearch(book: PhoneBook, linking: Boolean, onChoose: (path: String) -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    var text by rememberSaveable { mutableStateOf(listOfNotNull(book.title, book.authorList.firstOrNull()).joinToString(" ")) }
    var query by rememberSaveable { mutableStateOf(text) }
    var attempt by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<BookSearchResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query, attempt) {
        results = null
        error = null
        try {
            results = container.api.get("/books/search?q=${query.encodeURLParameter()}&page=1", BookSearchResponse.serializer()).results.take(LINK_RESULTS)
        } catch (e: ApiException) {
            error = e.message
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Find it in Carrel")
        Field(
            "Title, author, or ISBN",
            text,
            { text = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onDone = { if (text.isNotBlank()) query = text.trim() },
        )
        LinkButton("Search", { if (text.isNotBlank()) query = text.trim() }, color = colors.ink)
        val found = results
        when {
            error != null -> ErrorNotice(error.orEmpty(), { attempt++ })
            found == null -> Text("Searching…", style = Carrel.type.mono, color = colors.inkSoft)
            found.isEmpty() -> Text("Nothing found. Try fewer words, or the ISBN.", style = Carrel.type.body, color = colors.inkSoft)
            else -> Column {
                found.forEach { result ->
                    BookRow(
                        result.coverUrl,
                        result.title,
                        result.authors,
                        result.releaseYear,
                        Modifier.clickable(onClickLabel = "Link to this book", role = Role.Button, enabled = !linking) { onChoose(result.bookPath) }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRow(coverUrl: String?, title: String, authors: List<String>, year: Int?, modifier: Modifier = Modifier) {
    val colors = Carrel.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Cover(coverUrl, title, authors.firstOrNull(), 56.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Carrel.type.heading, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val byline = listOfNotNull(authors.takeIf { it.isNotEmpty() }?.let { "by ${listNames(it.take(3))}" }, year?.toString()).joinToString(" · ")
            if (byline.isNotEmpty()) Text(byline, style = Carrel.type.body, color = colors.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
