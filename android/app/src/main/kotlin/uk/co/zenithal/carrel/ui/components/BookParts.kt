package uk.co.zenithal.carrel.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import uk.co.zenithal.carrel.data.BookSuggestion
import uk.co.zenithal.carrel.data.GenreLink
import uk.co.zenithal.carrel.data.formatCount
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Cloth colours for books without a cover, as on the website. */
private val BINDINGS = listOf(0xFF6B2A1F, 0xFF2F4A3A, 0xFF2B3A55, 0xFF5A4A2A, 0xFF4A2B45, 0xFF1F4547).map(::Color)
private val BINDING_TEXT = Color(0xFFF3EDE1)
private val COVER_SHAPE = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp)

/**
 * A book cover at the given width, or a plain cloth binding with the title when there's no image (or it fails to
 * load). A zoomable cover opens larger when tapped; a binding has nothing more to show, so it doesn't.
 */
@Composable
fun Cover(url: String?, title: String, author: String?, width: Dp, modifier: Modifier = Modifier, zoomable: Boolean = false) {
    var zoomed by remember { mutableStateOf(false) }
    val frame = modifier
        .width(width)
        .aspectRatio(2f / 3f)
        .shadow(if (width < 80.dp) 2.dp else 6.dp, COVER_SHAPE)
        .clip(COVER_SHAPE)
        .background(Carrel.colors.rule)
    if (url == null) {
        Binding(title, author, width, frame)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = "Cover of $title",
        contentScale = ContentScale.Crop,
        error = { Binding(title, author, width, Modifier.fillMaxSize()) },
        modifier = if (zoomable) frame.clickable(onClickLabel = "View the cover larger", role = Role.Image) { zoomed = true } else frame,
    )
    if (zoomed) {
        ImageViewer(url, "Cover of $title") { zoomed = false }
    }
}

@Composable
private fun Binding(title: String, author: String?, width: Dp, modifier: Modifier) {
    val small = width < 80.dp
    val colour = BINDINGS[(title.fold(0L) { h, c -> (h * 31 + c.code) and 0xFFFFFFFFL } % BINDINGS.size).toInt()]
    Column(
        modifier
            .background(colour)
            .semantics { contentDescription = "No cover for $title" }
            .padding(horizontal = if (small) 6.dp else width * 0.1f, vertical = if (small) 8.dp else width * 0.12f),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val rule = BINDING_TEXT.copy(alpha = 0.5f)
        Text(
            title,
            style = if (small) Carrel.type.mono else Carrel.type.body,
            color = BINDING_TEXT,
            maxLines = if (small) 4 else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .drawBehind {
                    drawLine(rule, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .padding(vertical = 4.dp),
        )
        if (author != null && !small) {
            Text(author.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = BINDING_TEXT.copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

/** A picture shown larger over the page; tapping anywhere closes it. */
@Composable
fun ImageViewer(url: String, description: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(onClickLabel = "Close", onClick = onClose).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Hardcover's aggregate rating. Hardcover's terms require crediting it whenever it's shown, linked when the book's known. */
@Composable
fun HardcoverRating(rating: Double?, count: Int?, hardcoverId: Int? = null, modifier: Modifier = Modifier) {
    if (rating == null || count == null || count == 0) return
    val colors = Carrel.colors
    val context = LocalContext.current
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.accent)) { append("★ ") }
        withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.Medium)) { append("%.2f".format(rating)) }
        append("  ${formatCount(count)} ${if (count == 1) "rating" else "ratings"} on ")
        withStyle(SpanStyle(textDecoration = if (hardcoverId != null) TextDecoration.Underline else null)) { append("Hardcover") }
    }
    Text(
        text,
        style = Carrel.type.mono,
        color = colors.inkSoft,
        modifier = if (hardcoverId != null) {
            modifier.clickable(onClickLabel = "Open on Hardcover") {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://hardcover.app/id/book/$hardcoverId".toUri()))
            }
        } else modifier,
    )
}

/** A small mono label with an accent border: Upcoming, You're here. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Carrel.type.mono.copy(letterSpacing = 0.08.em),
        color = Carrel.colors.accent,
        modifier = modifier
            .border(1.dp, Carrel.colors.accent, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private val THIS_YEAR = java.time.Year.now().value

/**
 * A titled row of suggested covers, swiped sideways, ending in the website's thick shelf edge; nothing when there are
 * none. Books not out yet are marked Upcoming, unless markUpcoming is false (on a shelf where they all are).
 */
@Composable
fun SuggestionShelf(
    title: String,
    books: List<BookSuggestion>,
    onOpen: (BookSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    markUpcoming: Boolean = true,
) {
    if (books.isEmpty()) return
    val colors = Carrel.colors
    Column(modifier.fillMaxWidth().padding(top = 48.dp)) {
        SectionTitle(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .drawBehind {
                    val edge = 6.dp.toPx()
                    drawLine(colors.ruleStrong, Offset(0f, size.height - edge / 2), Offset(size.width, size.height - edge / 2), edge)
                }
                .padding(bottom = 24.dp),
        ) {
            items(books, key = { it.hardcoverId }) { book ->
                val upcoming = markUpcoming && book.releaseYear != null && book.releaseYear > THIS_YEAR
                Column(
                    Modifier
                        .width(92.dp)
                        .clickable(onClickLabel = "Open", role = Role.Button) { onOpen(book) }
                        .semantics { contentDescription = describe(book) + if (upcoming) ". Upcoming" else "" },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Cover(book.coverUrl, book.title, book.authors.firstOrNull(), 92.dp)
                    if (upcoming) Badge("Upcoming")
                }
            }
        }
    }
}

/** Title, authors, and place in its series, for screen readers. */
private fun describe(book: BookSuggestion): String {
    val position = seriesPosition(book.seriesPosition)
    val series = book.seriesName?.let { " ($it${position?.let { p -> ", book $p" } ?: ""})" } ?: ""
    val authors = if (book.authors.isNotEmpty()) " by ${listNames(book.authors)}" else ""
    return "${book.title}$series$authors"
}

/** Genres as a row of bordered labels, each opening its page; `trailing` goes at the end (e.g. a link to them all). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreLinks(genres: List<GenreLink>, onOpen: (GenreLink) -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = Carrel.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        genres.forEach { genre ->
            Text(
                genre.name.uppercase(),
                style = Carrel.type.mono.copy(letterSpacing = 0.06.em),
                color = colors.inkSoft,
                modifier = Modifier
                    .border(1.dp, colors.ruleStrong, RoundedCornerShape(2.dp))
                    .then(if (genre.slug.isNotEmpty()) Modifier.clickable(role = Role.Button) { onOpen(genre) } else Modifier)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        trailing()
    }
}

/** A placeholder line while something loads. */
@Composable
fun SkeletonLine(width: Float, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(width).height(height).background(Carrel.colors.rule, RoundedCornerShape(2.dp)))
}

/** A row of placeholder covers, the shape of SuggestionShelf, while suggestions load. */
@Composable
fun ShelfSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SkeletonLine(0.3f)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { Box(Modifier.width(92.dp).aspectRatio(2f / 3f).background(Carrel.colors.rule, COVER_SHAPE)) }
        }
    }
}
