package uk.co.zenithal.carrel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.zenithal.carrel.data.Destination
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryBook
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.Store
import kotlin.math.roundToInt

/** The tab a launcher shortcut opens, as an extra on its intent. */
private const val SHORTCUT_TAB = "uk.co.zenithal.carrel.TAB"
private const val SEARCH_TAB = "search"
private const val LIBRARY_TAB = "library"
/** Books on the Reading shelf given shortcuts, most recently changed first. */
private const val SHORTCUT_BOOKS = 2

/** Where a launcher shortcut for a tab goes; null for any other intent. (A book's shortcut is a link to it.) */
fun shortcutDestination(intent: Intent): Destination? = when (intent.getStringExtra(SHORTCUT_TAB)) {
    SEARCH_TAB -> Destination.Search(null)
    LIBRARY_TAB -> Destination.Library
    else -> null
}

/**
 * The shortcuts that long-pressing the app's icon shows: Search, Library, and the books the reader is reading. They
 * follow the saved library, so they change with the Reading shelf, and the books go when the reader signs out (which
 * clears it).
 */
class LauncherShortcuts(private val context: Context, store: Store, scope: CoroutineScope) {
    init {
        scope.launch {
            store.saved(LIBRARY_PATH, LibrarySerializer)
                .map { items ->
                    items.orEmpty()
                        .filter { it.entry.status == ReadingStatus.Reading }
                        .sortedByDescending { it.entry.updatedAt }
                        .take(SHORTCUT_BOOKS)
                        .map { it.book }
                }
                .distinctUntilChanged()
                .collectLatest { books -> withContext(Dispatchers.IO) { publish(books) } }
        }
    }

    private suspend fun publish(books: List<LibraryBook>) {
        // Lower ranks sit nearer the icon.
        val tabs = listOf(
            tab(SEARCH_TAB, "Search", R.drawable.shortcut_search, rank = 0),
            tab(LIBRARY_TAB, "Library", R.drawable.shortcut_library, rank = 1),
        )
        val reading = books.mapIndexed { index, book ->
            ShortcutInfoCompat.Builder(context, "book-${book.id}")
                .setShortLabel(book.title)
                .setLongLabel(book.title)
                .setIcon(coverIcon(book.coverUrl) ?: IconCompat.createWithResource(context, R.drawable.shortcut_book))
                .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.WEBSITE_URL}/books/${book.id}"), context, MainActivity::class.java))
                .setRank(tabs.size + index)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, tabs + reading)
    }

    private fun tab(name: String, label: String, icon: Int, rank: Int) =
        ShortcutInfoCompat.Builder(context, name)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(Intent.ACTION_VIEW, null, context, MainActivity::class.java).putExtra(SHORTCUT_TAB, name))
            .setRank(rank)
            .build()

    /** The book's cover on the icon's paper, inside the part of the icon every launcher shape shows. */
    private suspend fun coverIcon(url: String?): IconCompat? {
        url ?: return null
        val request = ImageRequest.Builder(context).data(url).size(COVER_HEIGHT).allowHardware(false).build()
        val cover = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        val icon = createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val width = (COVER_HEIGHT * cover.width.toFloat() / cover.height).roundToInt().coerceAtMost(COVER_HEIGHT)
        val left = (ICON_SIZE - width) / 2
        val top = (ICON_SIZE - COVER_HEIGHT) / 2
        Canvas(icon).apply {
            drawColor(ContextCompat.getColor(context, R.color.icon_paper))
            drawBitmap(cover, null, Rect(left, top, left + width, top + COVER_HEIGHT), null)
        }
        return IconCompat.createWithAdaptiveBitmap(icon)
    }

    private companion object {
        /** An adaptive icon's 108 units, at 4 pixels each. */
        const val ICON_SIZE = 432
        /** Within the middle 72 units that every launcher's mask leaves showing. */
        const val COVER_HEIGHT = 264
    }
}
