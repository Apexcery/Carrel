package uk.co.zenithal.carrel.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.data.SeriesBook
import uk.co.zenithal.carrel.data.SeriesDetail
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.ui.components.Badge
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.HardcoverRating
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel

private val THIS_YEAR = java.time.Year.now().value

/** A series in reading order, as on the website. `currentBook` (a Hardcover id) marks the book the reader came from. */
@Composable
fun SeriesScreen(hardcoverId: Int, currentBook: Int?, openBook: (path: String) -> Unit) {
    val series = rememberLoaded("/series/hardcover/$hardcoverId", SeriesDetail.serializer())
    val data = series.loaded.data
    Column(Modifier.fillMaxSize().background(Carrel.colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        when {
            data != null -> SeriesView(data, currentBook, openBook)
            series.loaded.error != null -> ErrorNotice(series.loaded.error.message.orEmpty(), series.retry)
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(0.3f)
                SkeletonLine(0.7f, 36.dp)
                repeat(4) { SkeletonLine(1f, 60.dp) }
            }
        }
    }
}

@Composable
private fun SeriesView(series: SeriesDetail, currentBook: Int?, openBook: (String) -> Unit) {
    val colors = Carrel.colors
    Kicker("Series")
    Text(series.name, style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    val count = series.books.size
    val meta = listOfNotNull(
        series.author?.let { "by $it" },
        "$count ${if (count == 1) "book" else "books"}" + if (series.isCompleted == true) " · complete" else "",
    ).joinToString(" · ")
    Text(meta, style = Carrel.type.mono, color = colors.inkSoft)

    if (series.books.isEmpty() && series.otherBooks.isEmpty()) {
        Text("Hardcover doesn’t list any books in this series yet.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 28.dp))
    }
    if (series.books.isNotEmpty()) {
        Column(Modifier.padding(top = 28.dp)) { SeriesList(series.books, currentBook, openBook) }
    }
    if (series.otherBooks.isNotEmpty()) {
        Column(Modifier.padding(top = 48.dp)) {
            SectionTitle("Also in this series")
            SeriesList(series.otherBooks, currentBook, openBook)
        }
    }
}

@Composable
private fun SeriesList(books: List<SeriesBook>, currentBook: Int?, openBook: (String) -> Unit) {
    books.forEachIndexed { index, book ->
        if (index > 0) HorizontalDivider(color = Carrel.colors.rule)
        SeriesItem(book, book.hardcoverId == currentBook, openBook)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesItem(book: SeriesBook, current: Boolean, openBook: (String) -> Unit) {
    val colors = Carrel.colors
    val upcoming = book.releaseYear != null && book.releaseYear > THIS_YEAR
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) colors.accent.copy(alpha = 0.06f) else colors.paper)
            .clickable(role = Role.Button) { openBook("/books/hardcover/${book.hardcoverId}") }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            seriesPosition(book.position) ?: "·",
            style = Carrel.type.heading,
            color = colors.accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Cover(book.coverUrl, book.title, book.authors.firstOrNull(), 56.dp)
        Column(Modifier.weight(1f)) {
            Text(book.title, style = Carrel.type.heading, color = colors.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                val byline = listOfNotNull(
                    book.authors.takeIf { it.isNotEmpty() }?.let { "by ${listNames(it)}" },
                    book.releaseYear?.toString(),
                ).joinToString(" · ")
                if (byline.isNotEmpty()) Text(byline, style = Carrel.type.body, color = colors.inkSoft)
                if (upcoming) Badge("Upcoming")
                if (current) Badge("You’re here")
            }
            HardcoverRating(book.hardcoverRating, book.hardcoverRatingsCount, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
