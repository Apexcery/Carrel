package uk.co.zenithal.carrel.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.serialization.builtins.ListSerializer
import uk.co.zenithal.carrel.data.BookSuggestion
import uk.co.zenithal.carrel.data.DiscoverShelves
import uk.co.zenithal.carrel.data.GenrePicks
import uk.co.zenithal.carrel.data.HomeTab
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.Loaded
import uk.co.zenithal.carrel.data.RelatedBooks
import uk.co.zenithal.carrel.data.favouriteAuthorBooks
import uk.co.zenithal.carrel.data.homeTab
import uk.co.zenithal.carrel.data.suggestionBasis
import uk.co.zenithal.carrel.ui.Loadable
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.ShelfSkeleton
import uk.co.zenithal.carrel.ui.components.SuggestionShelf
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.rememberOwnedHardcoverIds
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Books shown in each row of suggestions. */
private const val SUGGESTIONS_SHOWN = 12

private val SUGGESTIONS = ListSerializer(BookSuggestion.serializer())

/**
 * Suggestions, as the website's home page: signed in, in two tabs (For you, from the reader's library, and Discover,
 * the same for everyone); signed out, just Discover. The reader's own shelves are in the Library tab.
 */
@Composable
fun HomeScreen(signedIn: Boolean, openBook: (path: String) -> Unit) {
    val colors = Carrel.colors
    var chosen by rememberSaveable { mutableStateOf<HomeTab?>(null) }
    val library = rememberLoaded(if (signedIn) LIBRARY_PATH else null, LibrarySerializer).loaded
    val tab = homeTab(signedIn, library.data, chosen)
    val open = { book: BookSuggestion -> openBook("/books/hardcover/${book.hardcoverId}") }

    Column(Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text("What are you reading?", style = Carrel.type.displayLarge, color = colors.ink)
        Text(
            "Search by title, author, or ISBN to find a book, its editions, and the series it belongs to.",
            style = Carrel.type.body,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = 18.dp),
        )
        if (signedIn) Tabs(tab) { chosen = it }
        when (tab) {
            HomeTab.ForYou -> ForYou(library, open) { chosen = HomeTab.Discover }
            HomeTab.Discover -> Discover(open)
        }
    }
}

/** For you and Discover, as the website's underlined tabs. */
@Composable
private fun Tabs(tab: HomeTab, onChoose: (HomeTab) -> Unit) {
    val colors = Carrel.colors
    Row(
        Modifier
            .padding(top = 28.dp)
            .fillMaxWidth()
            .drawBehind { drawLine(colors.rule, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
    ) {
        HomeTab.entries.forEachIndexed { index, option ->
            val current = option == tab
            Text(
                option.label.uppercase(),
                style = Carrel.type.mono.copy(letterSpacing = 0.1.em),
                color = if (current) colors.ink else colors.inkSoft,
                modifier = Modifier
                    .selectable(selected = current, role = Role.Tab) { onChoose(option) }
                    .drawBehind {
                        if (current) drawLine(colors.accent, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                    }
                    .padding(start = if (index == 0) 0.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * Suggestions from the reader's library. Like the website, it waits for every shelf before showing any, so they
 * appear together rather than one by one; a shelf that fails or has nothing to suggest is left out.
 */
@Composable
private fun ForYou(library: Loaded<List<LibraryItem>>, open: (BookSuggestion) -> Unit, showDiscover: () -> Unit) {
    val items = library.data
    val next = rememberLoaded("/library/next-in-series", SUGGESTIONS).loaded
    val genre = rememberLoaded("/library/genre-picks", GenrePicks.serializer()).loaded
    val basis = remember(items) { items?.let(::suggestionBasis) }
    val related = rememberLoaded(basis?.let { "/books/${it.item.book.id}/related" }, RelatedBooks.serializer()).loaded
    val owned = rememberOwnedHardcoverIds()
    // The reader's favourite author, or if they've shelved everything by them, the next favourite, up to three. Each
    // is only asked for once the one before has nothing left. Often the same book as above, so the same request.
    val authorBooks = remember(items) { items?.let(::favouriteAuthorBooks).orEmpty() }
    val first = moreBy(authorBooks.getOrNull(0), enabled = items != null, owned)
    val second = moreBy(authorBooks.getOrNull(1), enabled = first.exhausted, owned)
    val third = moreBy(authorBooks.getOrNull(2), enabled = second.exhausted, owned)
    val authors = listOf(first, second, third)

    val ready = settled(library) && settled(next) && settled(related) && settled(genre) && owned != null &&
        authors.all { settled(it.loaded) }
    if (!ready) {
        repeat(4) { ShelfSkeleton() }
        return
    }

    fun notOwned(books: List<BookSuggestion>) = books.filter { it.hardcoverId.toLong() !in owned!! }
    val moreBy = authors.firstNotNullOfOrNull { it.shelf }
    val because = basis?.let { b -> related.data?.let { b to notOwned(it.similar) } }
    val genreShelf = genre.data?.genre?.let { it to notOwned(genre.data.books) }

    if (next.data.isNullOrEmpty() && moreBy == null && because?.second.isNullOrEmpty() && genreShelf?.second.isNullOrEmpty()) {
        EmptyForYou(showDiscover)
        return
    }
    next.data?.let { SuggestionShelf("Next in your series", it.take(SUGGESTIONS_SHOWN), open) }
    moreBy?.let { (author, books) -> SuggestionShelf("More by $author", books.take(SUGGESTIONS_SHOWN), open) }
    because?.let { (b, books) ->
        SuggestionShelf("Because you ${if (b.liked) "liked" else "read"} ${b.item.book.title}", books.take(SUGGESTIONS_SHOWN), open)
    }
    genreShelf?.let { (name, books) -> SuggestionShelf("Popular in $name", books.take(SUGGESTIONS_SHOWN), open) }
}

/** One link in the "More by" chain: its books, less those the reader has, and whether it has nothing to show. */
private class MoreBy(val loaded: Loaded<RelatedBooks>, val shelf: Pair<String, List<BookSuggestion>>?, val exhausted: Boolean)

/**
 * More by the author of this library book, leaving out books the reader has. Exhausted when there's nothing to show:
 * no such book, the request failed, or the reader already has everything listed (once their books have loaded).
 */
@Composable
private fun moreBy(item: LibraryItem?, enabled: Boolean, owned: Set<Long>?): MoreBy {
    val loaded = rememberLoaded(if (enabled && item != null) "/books/${item.book.id}/related" else null, RelatedBooks.serializer()).loaded
    val data = loaded.data
    val books = if (data != null && owned != null) data.byAuthor.filter { it.hardcoverId.toLong() !in owned } else null
    val author = data?.author
    return MoreBy(
        loaded,
        shelf = if (author != null && !books.isNullOrEmpty()) author to books else null,
        exhausted = item == null || (data == null && loaded.error != null) || (data != null && (author == null || books?.isEmpty() == true)),
    )
}

/** Finished, failed, or not needed; a saved copy counts, since it shows at once. */
private fun settled(loaded: Loaded<*>) = loaded.data != null || loaded.error != null || !loaded.refreshing

@Composable
private fun EmptyForYou(showDiscover: () -> Unit) {
    val colors = Carrel.colors
    val text = buildAnnotatedString {
        append("Shelve and finish a few books, and suggestions based on them will show up here. Until then, have a look in ")
        withLink(
            LinkAnnotation.Clickable("discover", TextLinkStyles(SpanStyle(color = colors.ink, textDecoration = TextDecoration.Underline))) { showDiscover() },
        ) { append("Discover") }
        append(".")
    }
    Text(text, style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 32.dp))
}

/** The same shelves for everyone: top rated, new, popular, and coming soon. */
@Composable
private fun Discover(open: (BookSuggestion) -> Unit) {
    val discover: Loadable<DiscoverShelves> = rememberLoaded("/discover", DiscoverShelves.serializer())
    val shelves = discover.loaded.data
    when {
        shelves != null -> {
            SuggestionShelf("Top rated", shelves.topRated.take(SUGGESTIONS_SHOWN), open)
            SuggestionShelf("New releases", shelves.newReleases.take(SUGGESTIONS_SHOWN), open)
            SuggestionShelf("Popular this month", shelves.popular.take(SUGGESTIONS_SHOWN), open)
            SuggestionShelf("Coming soon", shelves.comingSoon.take(SUGGESTIONS_SHOWN), open, markUpcoming = false)
        }
        discover.loaded.error != null -> ErrorNotice(discover.loaded.error.message.orEmpty(), discover.retry, Modifier.padding(top = 32.dp))
        else -> repeat(4) { ShelfSkeleton() }
    }
}
