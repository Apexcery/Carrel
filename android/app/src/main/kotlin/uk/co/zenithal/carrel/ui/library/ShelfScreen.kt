package uk.co.zenithal.carrel.ui.library

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.edit
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryItem
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.SortDirection
import uk.co.zenithal.carrel.data.SortKey
import uk.co.zenithal.carrel.data.defaultSort
import uk.co.zenithal.carrel.data.directionLabel
import uk.co.zenithal.carrel.data.filterItems
import uk.co.zenithal.carrel.data.formatDate
import uk.co.zenithal.carrel.data.lastFinished
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.naturalDirection
import uk.co.zenithal.carrel.data.sortItems
import uk.co.zenithal.carrel.data.sortOptions
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.ShelfSkeleton
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import kotlin.math.roundToInt

private enum class ShelfView(val label: String, val icon: ImageVector) {
    Grid("Show as a grid", GridIcon),
    List("Show as a list", ListIcon),
}

private const val VIEW_PREFS = "library"
private const val VIEW_KEY = "shelfView"

/**
 * Every book on one of the reader's shelves, with a filter and sort, as covers or as a list. The view is remembered on
 * the device for every shelf; the filter and sort only while the shelf is open.
 */
@Composable
fun ShelfScreen(status: ReadingStatus, openBook: (path: String) -> Unit) {
    val colors = Carrel.colors
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    val prefs = LocalContext.current.getSharedPreferences(VIEW_PREFS, Context.MODE_PRIVATE)
    var view by remember { mutableStateOf(prefs.getString(VIEW_KEY, null)?.let { runCatching { ShelfView.valueOf(it) }.getOrNull() } ?: ShelfView.Grid) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(defaultSort(status)) }
    var direction by rememberSaveable { mutableStateOf(naturalDirection(defaultSort(status))) }

    val all = library.loaded.data?.filter { it.entry.status == status }
    val items = all?.let { sortItems(filterItems(it, query), sort, direction) }
    val grid = view == ShelfView.Grid

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        modifier = Modifier.fillMaxSize().background(colors.paper),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 28.dp else 16.dp),
    ) {
        whole {
            Column {
                Kicker("Your library")
                Text(status.label, style = Carrel.type.displayLarge, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
                all?.let {
                    val count = if (query.isNotBlank()) "${items!!.size} of ${it.size}" else "${it.size}"
                    Text("$count ${if (it.size == 1) "book" else "books"}", style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        when {
            all == null -> whole {
                library.loaded.error?.let { ErrorNotice(it.message.orEmpty(), library.retry) } ?: ShelfSkeleton()
            }
            all.isEmpty() -> whole { Text("Nothing on this shelf yet.", style = Carrel.type.body, color = colors.inkSoft) }
            else -> {
                whole {
                    Toolbar(status, query, { query = it }, sort, direction, view, { key ->
                        // A new sort starts in its natural direction (A–Z, newest first, highest first).
                        sort = key
                        direction = naturalDirection(key)
                    }, { direction = if (direction == SortDirection.Ascending) SortDirection.Descending else SortDirection.Ascending }) {
                        view = it
                        prefs.edit { putString(VIEW_KEY, it.name) }
                    }
                }
                if (items!!.isEmpty()) {
                    whole { Text("No books on this shelf match “$query”.", style = Carrel.type.body, color = colors.inkSoft) }
                }
                items(items, key = { it.entry.id }) { item ->
                    if (grid) GridBook(item, openBook) else ListBook(item, status, openBook)
                }
            }
        }
    }
}

/** An item across the whole width of the grid. */
private fun LazyGridScope.whole(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    status: ReadingStatus,
    query: String,
    onQuery: (String) -> Unit,
    sort: SortKey,
    direction: SortDirection,
    view: ShelfView,
    onSort: (SortKey) -> Unit,
    onReverse: () -> Unit,
    onView: (ShelfView) -> Unit,
) {
    val colors = Carrel.colors
    var choosingSort by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Field("Filter by title, author, or series", query, onQuery)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FieldLabel("Sort")
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Wraps onto a second line only when a long sort and a large font don't fit beside the view switch.
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            "${sort.label} ▾",
                            style = Carrel.type.mono,
                            color = colors.ink,
                            modifier = Modifier.clickable(onClickLabel = "Choose the order", role = Role.DropdownList) { choosingSort = true }.padding(vertical = 8.dp),
                        )
                        DropdownMenu(choosingSort, { choosingSort = false }, containerColor = colors.paperRaised) {
                            sortOptions(status).forEach { key ->
                                DropdownMenuItem(
                                    text = { Text(key.label, style = Carrel.type.body, color = if (key == sort) colors.accent else colors.ink) },
                                    onClick = {
                                        choosingSort = false
                                        onSort(key)
                                    },
                                )
                            }
                        }
                    }
                    // The arrow alone doesn't say what "up" means for this sort, so the label goes with it.
                    Text(
                        "${if (direction == SortDirection.Ascending) "↑" else "↓"} ${directionLabel(sort, direction)}",
                        style = Carrel.type.mono,
                        color = colors.inkSoft,
                        modifier = Modifier.clickable(onClickLabel = "Reverse the order", role = Role.Button, onClick = onReverse).padding(vertical = 8.dp),
                    )
                }
                ViewSwitch(view, onView)
            }
        }
    }
}

/** Grid or list, as two icons joined into one switch, with the chosen one filled. */
@Composable
private fun ViewSwitch(view: ShelfView, onView: (ShelfView) -> Unit) {
    val colors = Carrel.colors
    val shape = RoundedCornerShape(2.dp)
    Row(Modifier.padding(start = 12.dp).height(IntrinsicSize.Min).border(1.dp, colors.ruleStrong, shape).clip(shape)) {
        ShelfView.entries.forEachIndexed { index, option ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.ruleStrong))
            val chosen = option == view
            Box(
                Modifier
                    .background(if (chosen) colors.accent else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton) { onView(option) }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Icon(option.icon, contentDescription = option.label, tint = if (chosen) colors.onAccent else colors.inkSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The "layout-grid" and "list" icons from Lucide (lucide.dev, ISC licence), the website's icon set. */
private val GridIcon = lucide("Grid", "M3 3h7v7H3z", "M14 3h7v7h-7z", "M14 14h7v7h-7z", "M3 14h7v7H3z")
private val ListIcon = lucide("List", "M3 6h.01", "M3 12h.01", "M3 18h.01", "M8 6h13", "M8 12h13", "M8 18h13")

/** A 24-unit icon drawn with Lucide's 2-unit round strokes, tinted by Icon. */
private fun lucide(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    paths.forEach { d ->
        addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

/** A cover with its title, author, and the reader's rating under it. */
@Composable
private fun GridBook(item: LibraryItem, openBook: (String) -> Unit) {
    val colors = Carrel.colors
    BoxWithConstraints(Modifier.clickable(onClickLabel = "Open", role = Role.Button) { openBook("/books/${item.book.id}") }) {
        val width = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Cover(item.book.coverUrl, item.book.title, item.book.authors.firstOrNull(), width, Modifier.padding(bottom = 6.dp))
            Text(item.book.title, style = Carrel.type.body.copy(lineHeight = 1.25.em), color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.book.authors.isNotEmpty()) {
                Text(listNames(item.book.authors), style = Carrel.type.mono, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.entry.rating?.let { StarDisplay(it, Modifier.padding(top = 2.dp)) }
        }
    }
}

/** One book to a row: a small cover, then its title, author, rating, and where the reader is with it. */
@Composable
private fun ListBook(item: LibraryItem, status: ReadingStatus, openBook: (String) -> Unit) {
    val colors = Carrel.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open", role = Role.Button) { openBook("/books/${item.book.id}") },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(item.book.coverUrl, item.book.title, item.book.authors.firstOrNull(), 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.book.title, style = Carrel.type.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.book.authors.isNotEmpty()) {
                Text(listNames(item.book.authors), style = Carrel.type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item.entry.rating?.let { StarDisplay(it) }
                listDetail(item, status)?.let { Text(it, style = Carrel.type.mono, color = colors.inkSoft) }
            }
        }
    }
}

/** Where the reader is with the book: progress while reading, when it was finished, or when it was added. */
private fun listDetail(item: LibraryItem, status: ReadingStatus): String? = when {
    status.inProgress -> item.entry.progressPercent?.let { "${it.roundToInt()}%" } ?: "Just started"
    status == ReadingStatus.WantToRead -> formatDate(item.entry.addedAt.take(10))?.let { "Added $it" }
    else -> formatDate(lastFinished(item))
}
