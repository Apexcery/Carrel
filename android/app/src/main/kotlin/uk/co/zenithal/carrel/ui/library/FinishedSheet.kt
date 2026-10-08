package uk.co.zenithal.carrel.ui.library

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.SeriesBook
import uk.co.zenithal.carrel.data.SeriesDetail
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.nextInSeries
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.data.toRequest
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import kotlin.random.Random

/**
 * Shown when the reader marks a book as read: a little paper confetti, their rating (only if they hadn't rated it),
 * and the next book in its series, to add to Want to read. Swiped away, or closed with Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedSheet(book: BookDetail, openBook: (path: String) -> Unit, onClose: () -> Unit) {
    val colors = Carrel.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val items = rememberLoaded(LIBRARY_PATH, LibrarySerializer).loaded.data
    val entry = items?.firstOrNull { it.book.id == book.id }?.entry
    // Taken as the sheet opens, so the rating stays after it's chosen, and the next book after it's added.
    val unrated = remember { entry?.rating == null }
    val owned = remember { items?.mapNotNull { it.book.hardcoverId }?.toSet().orEmpty() }
    var ratingError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Finished!", style = Carrel.type.displayLarge, color = colors.ink)
                    Text(
                        book.title,
                        style = Carrel.type.body.copy(fontStyle = FontStyle.Italic),
                        color = colors.inkSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (unrated && entry != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldLabel("How was it?")
                        StarRating(entry.rating, { value ->
                            scope.launch {
                                ratingError = null
                                try {
                                    container.library.save(book, entry.toRequest().copy(rating = value))
                                } catch (e: ApiException) {
                                    ratingError = e.message
                                }
                            }
                        }, enabled = true)
                        ratingError?.let { FormMessage(it, Tone.Error) }
                    }
                }
                NextInSeries(book, owned, openBook)
                LinkButton("Done", onClose)
            }
            Confetti(Modifier.fillMaxWidth().height(220.dp))
        }
    }
}

/** The book after this one in its main series, if the reader hasn't got it, with a button to add it to Want to read. */
@Composable
private fun NextInSeries(book: BookDetail, owned: Set<Long>, openBook: (String) -> Unit) {
    val colors = Carrel.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    // The main series comes first (see the API's BookService).
    val main = book.series.firstOrNull()?.takeIf { it.hardcoverId != null && it.position != null }
    val series = rememberLoaded(main?.let { "/series/hardcover/${it.hardcoverId}" }, SeriesDetail.serializer()).loaded.data
    val next: SeriesBook = series?.let { nextInSeries(it, main!!.position!!, owned) } ?: return
    var adding by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Next in the series")
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open", role = Role.Button) { openBook("/books/hardcover/${next.hardcoverId}") },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cover(next.coverUrl, next.title, next.authors.firstOrNull(), 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(next.title, style = Carrel.type.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (next.authors.isNotEmpty()) {
                    Text(listNames(next.authors), style = Carrel.type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(seriesPosition(next.position)?.let { "Book $it" }, series.name).joinToString(" · "),
                    style = Carrel.type.mono,
                    color = colors.inkSoft,
                )
            }
        }
        if (added) {
            Text("Added to Want to read.", style = Carrel.type.mono, color = colors.inkSoft)
        } else {
            PrimaryButton(if (adding) "Adding…" else "Add to Want to read", {
                scope.launch {
                    adding = true
                    error = null
                    try {
                        // Opening the book stores it on the server, which a library entry needs.
                        val detail = container.api.get("/books/hardcover/${next.hardcoverId}", BookDetail.serializer())
                        container.library.save(detail, null.toRequest())
                        added = true
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        adding = false
                    }
                }
            }, enabled = !adding)
        }
        error?.let { FormMessage(it, Tone.Error) }
    }
}

/** A scrap of paper falling: where it starts across the width, when, how fast, how it turns, and its colour. */
private class Scrap(val x: Float, val delay: Float, val speed: Float, val spin: Float, val wide: Boolean, val color: Color)

/** Scraps of paper falling once, over the top of the sheet; none when the phone's animations are turned off. */
@Composable
private fun Confetti(modifier: Modifier) {
    val colors = Carrel.colors
    val context = LocalContext.current
    val animate = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    if (!animate) return
    val progress = remember { Animatable(0f) }
    val scraps = remember {
        val palette = listOf(colors.accent, colors.accent, colors.ink, colors.inkSoft, colors.ruleStrong)
        List(SCRAPS) {
            Scrap(Random.nextFloat(), Random.nextFloat() * 0.35f, 0.7f + Random.nextFloat() * 0.6f, Random.nextFloat() * 720f - 360f, Random.nextBoolean(), palette.random())
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(FALL_MS, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(modifier) {
        val scrap = Size(4.dp.toPx(), 9.dp.toPx())
        scraps.forEach { s ->
            val t = ((progress.value - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val centre = Offset(s.x * size.width, -scrap.height + t * s.speed * (size.height + scrap.height * 2))
            val shape = if (s.wide) Size(scrap.height, scrap.width) else scrap
            rotate(s.spin * t, centre) {
                drawRect(s.color.copy(alpha = 1f - t * t), centre - Offset(shape.width / 2, shape.height / 2), shape)
            }
        }
    }
}

private const val SCRAPS = 40
private const val FALL_MS = 1800
