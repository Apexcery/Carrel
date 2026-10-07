package uk.co.zenithal.carrel.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.shelfItems
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.ShelfSkeleton
import uk.co.zenithal.carrel.ui.components.shelfEdge
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import kotlin.math.roundToInt

/** Shelves shown as rows of covers, after Reading; each title opens the whole shelf. */
private val SHELVES = listOf(ReadingStatus.WantToRead, ReadingStatus.Paused, ReadingStatus.Read, ReadingStatus.DidNotFinish)
/** Books shown in each shelf's row; the shelf's title opens the rest. */
private const val SHELF_BOOKS = 12

/** The signed-in reader's library, as the website's library column: Reading as cards, then a row for each shelf. */
@Composable
fun LibraryScreen(openBook: (path: String) -> Unit, openShelf: (ReadingStatus) -> Unit) {
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    val items = library.loaded.data
    val colors = Carrel.colors
    Column(
        Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text("Your library", style = Carrel.type.displayLarge, color = colors.ink)
        when {
            items != null -> {
                Text("${items.size} ${if (items.size == 1) "book" else "books"}", style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
                if (items.isEmpty()) {
                    Gap(24)
                    // Importing joins this once the app has Settings.
                    Text(
                        "Nothing on your shelves yet. Find a book in Search, and add it to your library from its page.",
                        style = Carrel.type.body,
                        color = colors.inkSoft,
                    )
                }
                val reading = shelfItems(items, ReadingStatus.Reading)
                if (reading.isNotEmpty()) ReadingShelf(reading, openBook) { openShelf(ReadingStatus.Reading) }
                SHELVES.forEach { status ->
                    val shelf = shelfItems(items, status)
                    if (shelf.isNotEmpty()) CoverShelf(status, shelf, openBook) { openShelf(status) }
                }
            }
            library.loaded.error != null -> {
                Gap(24)
                ErrorNotice(library.loaded.error.message.orEmpty(), library.retry)
            }
            else -> repeat(3) { ShelfSkeleton() }
        }
    }
}

/** A shelf's title and count, opening the whole shelf. */
@Composable
private fun ShelfHeading(status: ReadingStatus, count: Int, onOpen: () -> Unit) {
    val colors = Carrel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open the whole shelf", role = Role.Button, onClick = onOpen)
            .underRule(colors.rule)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${status.label} ($count)".uppercase(),
            style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
            color = colors.inkSoft,
            modifier = Modifier.weight(1f),
        )
        Text("SEE ALL →", style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = colors.accent)
    }
}

/** Books being read, as cards with their progress, swiped sideways. */
@Composable
private fun ReadingShelf(items: List<LibraryItem>, openBook: (String) -> Unit, openShelf: () -> Unit) {
    val colors = Carrel.colors
    Column(Modifier.padding(top = 40.dp)) {
        ShelfHeading(ReadingStatus.Reading, items.size, openShelf)
        LazyRow(
            Modifier.padding(top = 18.dp).fillMaxWidth().shelfEdge(colors.ruleStrong),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.entry.id }) { item ->
                Row(
                    Modifier
                        // The whole width for one book; with more, the next card shows at the edge.
                        .fillParentMaxWidth(if (items.size == 1) 1f else 0.85f)
                        // Room for the card's shadow, which the row would otherwise clip.
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .shadow(3.dp, RoundedCornerShape(1.dp, 1.dp, 3.dp, 3.dp))
                        .background(colors.paperRaised)
                        .clickable(onClickLabel = "Open", role = Role.Button) { openBook("/books/${item.book.id}") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Cover(item.book.coverUrl, item.book.title, item.book.authors.firstOrNull(), 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.book.title, style = Carrel.type.body.copy(lineHeight = 1.15.em), color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.book.authors.isNotEmpty()) {
                            Text(listNames(item.book.authors), style = Carrel.type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ProgressBar(item.entry.progressPercent, Modifier.padding(top = 12.dp))
                        Text(
                            item.entry.progressPercent?.let { "${it.roundToInt()}%" } ?: "Just started",
                            style = Carrel.type.mono,
                            color = colors.inkSoft,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A shelf's first books as a row of covers with the reader's ratings. */
@Composable
private fun CoverShelf(status: ReadingStatus, items: List<LibraryItem>, openBook: (String) -> Unit, openShelf: () -> Unit) {
    val colors = Carrel.colors
    Column(Modifier.padding(top = 40.dp)) {
        ShelfHeading(status, items.size, openShelf)
        LazyRow(
            Modifier.padding(top = 18.dp).fillMaxWidth().shelfEdge(colors.ruleStrong),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items.take(SHELF_BOOKS), key = { it.entry.id }) { item ->
                Column(
                    Modifier
                        .width(92.dp)
                        .clickable(onClickLabel = "Open", role = Role.Button) { openBook("/books/${item.book.id}") }
                        .semantics(mergeDescendants = true) { contentDescription = item.book.title },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Cover(item.book.coverUrl, item.book.title, item.book.authors.firstOrNull(), 92.dp)
                    item.entry.rating?.let { StarDisplay(it) }
                }
            }
        }
    }
}
