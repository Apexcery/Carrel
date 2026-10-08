package uk.co.zenithal.carrel.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.data.Loaded
import uk.co.zenithal.carrel.data.Reader
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.formatCount
import uk.co.zenithal.carrel.data.shelfItems
import uk.co.zenithal.carrel.ui.Loadable
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.library.CoverShelf
import uk.co.zenithal.carrel.ui.library.ReadingShelf
import uk.co.zenithal.carrel.ui.library.ShelfHeading
import uk.co.zenithal.carrel.ui.library.ShelfScreen
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import java.time.LocalDateTime

/** Every shelf, Paused included, in the website's order for a profile. */
private val SHELVES = listOf(ReadingStatus.Reading, ReadingStatus.Paused, ReadingStatus.WantToRead, ReadingStatus.Read, ReadingStatus.DidNotFinish)

/** Another reader's profile (the website's /@username), opened from a link: read-only, as anyone sees it there. */
@Composable
fun ReaderScreen(username: String, openBook: (path: String) -> Unit, openShelf: (ReadingStatus) -> Unit) {
    val reader = rememberLoaded("/readers/$username", Reader.serializer())
    val data = reader.loaded.data
    val colors = Carrel.colors
    Column(Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        when {
            // First, since a saved copy stays after the profile goes private, as the website checks it first.
            reader.loaded.error?.status == 404 -> NotFound()
            data != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(data.avatarUrl, data.username)
                    Column(Modifier.padding(start = 18.dp)) {
                        Text("@${data.username}", style = Carrel.type.displayMedium, color = colors.ink)
                        Text(
                            "${formatCount(data.library.size)} ${if (data.library.size == 1) "book" else "books"}",
                            style = Carrel.type.mono,
                            color = colors.inkSoft,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                if (data.library.isEmpty()) {
                    Text("@${data.username} hasn’t shelved any books yet.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 32.dp))
                } else {
                    SHELVES.forEach { status ->
                        val items = shelfItems(data.library, status)
                        when {
                            items.isEmpty() -> Column(Modifier.padding(top = 40.dp)) {
                                ShelfHeading(status, 0) { openShelf(status) }
                                Text("Nothing on this shelf yet.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 14.dp))
                            }
                            status == ReadingStatus.Reading -> ReadingShelf(items, openBook) { openShelf(status) }
                            else -> CoverShelf(status, items, openBook) { openShelf(status) }
                        }
                    }
                }
                val year = LocalDateTime.now().year
                ReadingGoal(data.library, data.goals, year, owner = data.username)
                YearInBooks(data.library, year, owner = data.username)
            }
            reader.loaded.error != null -> ErrorNotice(reader.loaded.error.message.orEmpty(), reader.retry)
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(0.5f, 36.dp)
                SkeletonLine(1f)
                SkeletonLine(0.6f)
            }
        }
    }
}

/** One of another reader's shelves (the website's /@username/shelves/read). */
@Composable
fun ReaderShelfScreen(username: String, status: ReadingStatus, openBook: (path: String) -> Unit) {
    val reader = rememberLoaded("/readers/$username", Reader.serializer())
    if (reader.loaded.error?.status == 404) {
        Column(Modifier.fillMaxSize().background(Carrel.colors.paper).padding(horizontal = 16.dp, vertical = 24.dp)) { NotFound() }
        return
    }
    val loaded = reader.loaded
    ShelfScreen(
        status,
        "@${loaded.data?.username ?: username}",
        Loadable(Loaded(loaded.data?.library, loaded.refreshing, loaded.error), reader.retry),
        openBook,
    )
}

/** A profile that doesn't exist or is private, which the API doesn't tell apart, worded as the website's. */
@Composable
private fun NotFound() {
    Kicker("Not on the shelf")
    Text("This page doesn’t exist.", style = Carrel.type.displayMedium, color = Carrel.colors.ink, modifier = Modifier.padding(top = 8.dp))
}
