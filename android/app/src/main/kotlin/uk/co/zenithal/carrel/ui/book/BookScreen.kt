package uk.co.zenithal.carrel.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.BookSuggestion
import uk.co.zenithal.carrel.data.Edition
import uk.co.zenithal.carrel.data.GenreLink
import uk.co.zenithal.carrel.data.RelatedBooks
import uk.co.zenithal.carrel.data.displaySubtitle
import uk.co.zenithal.carrel.data.formatDate
import uk.co.zenithal.carrel.data.genreLink
import uk.co.zenithal.carrel.data.formatDuration
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.otherCredits
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.GenreLinks
import uk.co.zenithal.carrel.ui.components.HardcoverRating
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.components.SuggestionShelf
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.rememberOwnedHardcoverIds
import uk.co.zenithal.carrel.ui.theme.Carrel

private const val EDITIONS_SHOWN = 6
private const val RELATED_SHOWN = 12

/**
 * A book's page, as on the website. `path` is how it was reached: Carrel's own id (/books/12), or a Hardcover or Open
 * Library id from search (which stores the book on the server). `shelf` is where the reader's shelf panel goes.
 */
@Composable
fun BookScreen(
    path: String,
    openBook: (path: String) -> Unit,
    openSeries: (hardcoverId: Int, fromBook: Int?) -> Unit,
    openGenre: (GenreLink) -> Unit,
    shelf: @Composable (BookDetail) -> Unit,
) {
    val book = rememberLoaded(path, BookDetail.serializer())
    val data = book.loaded.data
    Column(
        Modifier.fillMaxSize().background(Carrel.colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        when {
            data != null -> BookView(data, openBook, openSeries, openGenre, shelf)
            book.loaded.error != null -> ErrorNotice(book.loaded.error.message.orEmpty(), book.retry)
            else -> BookSkeleton()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BookView(
    book: BookDetail,
    openBook: (String) -> Unit,
    openSeries: (Int, Int?) -> Unit,
    openGenre: (GenreLink) -> Unit,
    shelf: @Composable (BookDetail) -> Unit,
) {
    val colors = Carrel.colors
    val authors = book.authors.filter { it.role == "author" }.map { it.name }
    val subtitle = displaySubtitle(book.title, book.subtitle)
    val mainSeries = book.series.firstOrNull()

    Cover(book.coverUrl, book.title, authors.firstOrNull(), 200.dp, zoomable = true)
    Gap(32)

    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(colors.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(bottom = 22.dp),
    ) {
        mainSeries?.let { series ->
            val position = seriesPosition(series.position)
            val seriesId = series.hardcoverId
            Text(
                ((position?.let { "Book $it · " } ?: "") + series.name).uppercase(),
                style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
                color = colors.accent,
                modifier = if (seriesId != null) Modifier.clickable(role = Role.Button) { openSeries(seriesId, book.hardcoverId) } else Modifier,
            )
        }
        Text(book.title, style = Carrel.type.displayLarge, color = colors.ink, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
        subtitle?.let { Text(it, style = Carrel.type.body.copy(fontStyle = FontStyle.Italic), color = colors.inkSoft) }
        if (authors.isNotEmpty()) Text("by ${listNames(authors)}", style = Carrel.type.body, color = colors.ink, modifier = Modifier.padding(top = 14.dp))
        otherCredits(book.authors).forEach { Text(it, style = Carrel.type.body, color = colors.inkSoft) }
    }

    FlowRow(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HardcoverRating(book.hardcoverRating, book.hardcoverRatingsCount, book.hardcoverId)
        book.firstPublishedYear?.let { Text("First published $it", style = Carrel.type.mono, color = colors.inkSoft) }
    }

    shelf(book)

    if (book.genres.isNotEmpty()) {
        GenreLinks(book.genres.map(::genreLink), openGenre, Modifier.padding(top = 24.dp, bottom = 30.dp))
    }

    book.description?.let { Description(it, book.descriptionSource) }

    if (book.series.isNotEmpty()) {
        Column(Modifier.padding(top = 48.dp)) {
            SectionTitle(if (book.series.size == 1) "Series" else "Series it belongs to")
            Gap(10)
            book.series.forEach { s ->
                val seriesId = s.hardcoverId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (seriesId != null) Modifier.clickable(role = Role.Button) { openSeries(seriesId, book.hardcoverId) } else Modifier)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(seriesPosition(s.position) ?: "–", style = Carrel.type.mono, color = colors.accent, modifier = Modifier.widthIn(min = 32.dp))
                    Column {
                        Text(s.name, style = Carrel.type.body, color = colors.ink)
                        if (seriesId != null) Text("See the whole series →", style = Carrel.type.mono, color = colors.inkFaint)
                    }
                }
            }
        }
    }

    if (book.editions.isNotEmpty()) Editions(book.editions)

    Related(book.id, openBook)
}

/** More by the book's author and books like it, leaving out any the reader already has. Hidden if they fail to load. */
@Composable
private fun Related(bookId: Long, openBook: (String) -> Unit) {
    val related = rememberLoaded("/books/$bookId/related", RelatedBooks.serializer()).loaded.data
    val owned = rememberOwnedHardcoverIds()
    if (related == null || owned == null) return
    fun notOwned(books: List<BookSuggestion>) = books.filter { it.hardcoverId.toLong() !in owned }.take(RELATED_SHOWN)
    val open = { b: BookSuggestion -> openBook("/books/hardcover/${b.hardcoverId}") }
    related.author?.let { SuggestionShelf("More by $it", notOwned(related.byAuthor), open) }
    SuggestionShelf("Readers might also like", notOwned(related.similar), open)
}

private val SOURCE_NAMES = mapOf("hardcover" to "Hardcover", "open_library" to "Open Library", "google_books" to "Google Books")

@Composable
private fun Description(text: String, source: String?) {
    val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        paragraphs.forEach { Text(it, style = Carrel.type.body.copy(lineHeight = 1.65.em), color = Carrel.colors.ink) }
        SOURCE_NAMES[source]?.let { Text("Description from $it", style = Carrel.type.mono, color = Carrel.colors.inkFaint) }
    }
}

/** Each edition as a catalogue card: format, publisher, details, and ISBN. Six, then the rest on request. */
@Composable
private fun Editions(editions: List<Edition>) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val colors = Carrel.colors
    Column(Modifier.padding(top = 48.dp)) {
        SectionTitle("Editions")
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            (if (showAll) editions else editions.take(EDITIONS_SHOWN)).forEach { edition ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(1.dp, 1.dp, 3.dp, 3.dp))
                        .background(colors.paperRaised)
                        .drawBehind { topRule(colors.accent, 2.dp.toPx()) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(formatLabel(edition.format).uppercase(), style = Carrel.type.monoMedium.copy(letterSpacing = 0.12.em), color = colors.accent)
                    Text(edition.publisher ?: "Unknown publisher", style = Carrel.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(
                        formatDate(edition.releaseDate),
                        edition.pageCount?.let { "$it pp." },
                        edition.audioSeconds?.let(::formatDuration),
                        edition.language?.uppercase(),
                    ).joinToString(" · ")
                    if (detail.isNotEmpty()) Text(detail, style = Carrel.type.mono, color = colors.inkSoft)
                    (edition.isbn13 ?: edition.isbn10)?.let { Text("ISBN $it", style = Carrel.type.mono, color = colors.inkSoft) }
                }
            }
        }
        if (editions.size > EDITIONS_SHOWN) {
            LinkButton(if (showAll) "Show fewer editions" else "Show all ${editions.size} editions", { showAll = !showAll }, Modifier.padding(top = 12.dp))
        }
    }
}

private fun formatLabel(format: String?) = when (format) {
    "print" -> "Print"
    "ebook" -> "Ebook"
    "audio" -> "Audiobook"
    else -> "Edition"
}

@Composable
private fun BookSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth(0.5f)) { SkeletonLine(1f, 300.dp) }
        Gap(18)
        SkeletonLine(0.3f)
        SkeletonLine(0.8f, 36.dp)
        SkeletonLine(0.6f)
        SkeletonLine(1f)
        SkeletonLine(1f)
    }
}

/** The card a signed-out reader sees where the shelf panel goes: what signing in would let them do. */
@Composable
fun ShelfPanelSignedOut(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    val colors = Carrel.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.paperRaised)
            .drawBehind { topRule(colors.accent, 3.dp.toPx()) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Kicker("Add to your library")
        Text("Sign in to shelve this book, rate it, and track your progress.", style = Carrel.type.body, color = colors.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Sign in", onSignIn)
            LinkButton("Create an account", onSignUp, color = colors.ink)
        }
    }
}

/** A rule of the given thickness along the top edge, like the website's border-top. */
private fun DrawScope.topRule(color: Color, width: Float) =
    drawLine(color, Offset(0f, width / 2), Offset(size.width, width / 2), width)
