@file:OptIn(ExperimentalReadiumApi::class)

package uk.co.zenithal.carrel.reader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import uk.co.zenithal.carrel.AppContainer
import uk.co.zenithal.carrel.CarrelApp
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.MainActivity
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.Download
import uk.co.zenithal.carrel.data.LibraryException
import uk.co.zenithal.carrel.data.PhoneBook
import uk.co.zenithal.carrel.data.PhoneBookException
import uk.co.zenithal.carrel.edgeToEdge
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.CarrelColors
import uk.co.zenithal.carrel.ui.theme.CarrelTheme
import uk.co.zenithal.carrel.ui.theme.Carrel
import uk.co.zenithal.carrel.ui.theme.ThemeChoice
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.epub.css.ColCount
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.ui.library.Choices
import uk.co.zenithal.carrel.ui.library.FieldLabel

/**
 * The book open in the reader, handed from the app to ReaderActivity (an activity can't be given a Publication), with
 * what reading it does to the library (null when it's not linked to a book in Carrel, or no one is signed in).
 * `locator` is where it opens, then where the reader is, so the page comes back when the screen rotates.
 */
class OpenBook(val book: PhoneBook, val publication: Publication, val progress: ReadingProgress?, var locator: Locator?) {
    /** A newer version of the book, downloaded while it's read, to reload into. */
    val newer = MutableStateFlow<PhoneBook?>(null)
    /** The copy the newer version replaced, deleted once the book closes (the reader still has it open). */
    var replaced: PhoneBook? = null
    /** A more recent place saved from another device, to go to. */
    val elsewhere = MutableStateFlow<ReadingPosition?>(null)
    /**
     * Whether the reader has moved from where the book opened. Only then is the place sent to Carrel, so opening a book
     * can't overwrite a more recent place from another device before it's been offered.
     */
    var moved = false
    /** The place waiting to be sent, a moment after a page turn. */
    var sending: Job? = null
}

/**
 * Opens a book from the phone in the reader, where it was left. A linked book moves to Reading as it opens, and a copy
 * never opened before starts at the progress in the library, e.g. from reading it in another app. A book from an online
 * library opens straight away and is checked for a newer version (new chapters of a serial, say) while it's read.
 */
suspend fun openReader(context: Context, container: AppContainer, book: PhoneBook) {
    val publication = container.phoneBooks.open(book)
    val signedIn = container.session.state.value is Session.SignedIn
    val progress = book.bookId?.takeIf { signedIn }?.let { ReadingProgress(container, it) }
    val saved = book.locator?.let { Locator.fromJSON(JSONObject(it)) }
    val start = when {
        saved == null -> progress?.startingPercent()?.let { publication.locateProgression(it / 100) }
        publication.linkWithHref(saved.href) != null -> saved
        // A newer version without the part the reader was in: as far through as before.
        else -> book.progression?.let { publication.locateProgression(it) }
    }
    closeOpenBook(container)
    val open = OpenBook(book, publication, progress, start)
    container.openBook = open
    progress?.let { container.scope.launch { it.start() } }
    context.startActivity(Intent(context, ReaderActivity::class.java).putExtra(ReaderActivity.BOOK, book.id))
    if (book.downloadUrl != null) container.scope.launch { checkForNewer(container, open) }
    progress?.let { container.scope.launch { syncPosition(open, it) } }
}

/**
 * Compares the place in the book saved in Carrel (from any device) with this phone's: a more recent one elsewhere is
 * offered to go to, and a more recent one here is sent, e.g. after reading without a signal.
 */
private suspend fun syncPosition(open: OpenBook, progress: ReadingProgress) {
    val book = open.book
    val localAt = book.openedAt?.let(Instant::ofEpochMilli)
    val remote = progress.savedPosition()
    val remoteAt = remote?.let { runCatching { OffsetDateTime.parse(it.updatedAt).toInstant() }.getOrNull() }
    when {
        remote != null && remoteAt != null && isNewerElsewhere(remote, remoteAt, if (book.locator == null) null else localAt, book.progression) ->
            open.elsewhere.value = remote
        book.locator != null && localAt != null && (remoteAt == null || localAt.isAfter(remoteAt)) ->
            progress.savePosition(book.locator, book.progression ?: 0.0, localAt)
    }
}

/** Closes the book that's open in the reader, deleting any copy a newer version replaced while it was read. */
internal fun closeOpenBook(container: AppContainer) {
    val open = container.openBook ?: return
    container.openBook = null
    open.publication.close()
    open.replaced?.let(container.phoneBooks::deleteCopy)
}

/**
 * Downloads a newer version of a book from an online library while it's read, and offers it to reload into (the next
 * time it opens uses it anyway). Nothing happens if it's unchanged, or the library can't be reached (away from home).
 */
private suspend fun checkForNewer(container: AppContainer, open: OpenBook) {
    val book = open.book
    val url = book.downloadUrl ?: return
    if (book.libraryId == null || container.libraries.get(book.libraryId) == null) return
    try {
        val download = container.libraries.download(url, container.phoneBooks.newFile(), book.etag, book.lastModified)
        if (download !is Download.Fetched) return
        val updated = container.phoneBooks.replace(book, download)
        if (updated.file == book.file) return
        open.replaced = book
        // Closed while it downloaded: the old copy can go now.
        if (container.openBook !== open) container.phoneBooks.deleteCopy(book) else open.newer.value = updated
    } catch (_: LibraryException) {
    } catch (_: PhoneBookException) {
    }
}

/**
 * Reads an EPUB from the phone with Readium, full screen. Tapping the middle shows the controls (contents, and how the
 * book looks); tapping the edges or swiping turns the page. Colours follow the app's theme, or the reader's own.
 */
class ReaderActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var open: OpenBook? = null
    /** Closing only to open again (see [reopen]), so the book stays open, and where it was is kept. */
    private var reopening = false
    private lateinit var prefs: SharedPreferences
    private lateinit var columns: ColumnCount
    /** The screen showing in the file on screen, from Readium's page turns. */
    private val turns = MutableStateFlow<PageTurn?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        container = (application as CarrelApp).container
        val open = container.openBook?.takeIf { it.book.id == intent.getLongExtra(BOOK, 0) }
        if (open == null) {
            // Android closed the app while the book was open: it's opened again from Carrel.
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        this.open = open
        val prefs = getSharedPreferences("reader", MODE_PRIVATE).also { prefs = it }
        val dark = when (container.appearance.theme.value) {
            ThemeChoice.Auto -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            ThemeChoice.Light -> false
            ThemeChoice.Dark -> true
        }
        val accent = container.appearance.accent.value
        val look = prefs.look
        // Fixed while the reader is open; a change of pages side by side opens it afresh (see reopen).
        val columns = look.pages.columns(resources.configuration.screenWidthDp >= TWO_PAGES_WIDTH).also { columns = it }
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(open.publication).createFragmentFactory(
            initialLocator = open.locator,
            initialPreferences = readerPreferences(look, look.theme.colors(dark, accent), columns),
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                    if (reopening) return
                    val file = open.publication.readingOrder.indexOfFirstWithHref(locator.href) ?: return
                    turns.value = PageTurn(file, pageIndex, totalPages)
                }
            },
            configuration = EpubNavigatorFragment.Configuration(readiumCssRsProperties = layoutFor(columns)).apply { addReaderFonts() },
        )
        super.onCreate(savedInstanceState)
        setContent {
            val theme by container.appearance.theme.collectAsState()
            val chosenAccent by container.appearance.accent.collectAsState()
            val darkNow = when (theme) {
                ThemeChoice.Auto -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            var lookNow by remember { mutableStateOf(look) }
            // The whole reader, its bars and sheets too, takes the reader's colours.
            val colors = lookNow.theme.colors(darkNow, chosenAccent)
            DisposableEffect(colors.isDark) {
                edgeToEdge(colors.isDark)
                onDispose {}
            }
            CompositionLocalProvider(LocalContainer provides container) {
                CarrelTheme(colors = colors) {
                    ReaderScreen(open, prefs, lookNow, { changed ->
                        lookNow = changed
                        prefs.saveLook(changed)
                    }, { it.colors(darkNow, chosenAccent) }, columns, turns, ::reopen, { reopening }, ::reload, ::showSystemBars, ::leave)
                }
            }
        }
    }

    /**
     * Folding, unfolding, or turning the phone: if that changes the pages side by side, the reader opens again. It must
     * be straight away, before the resized page (still on the same page number, now much further on or back) is taken
     * as the reader's place.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (open != null && prefs.look.pages.columns(newConfig.screenWidthDp >= TWO_PAGES_WIDTH) != columns) reopen()
    }

    override fun onStop() {
        super.onStop()
        val open = open ?: return
        val progress = open.progress ?: return
        val locator = open.locator
        container.scope.launch {
            progress.save()
            if (locator != null && !reopening && open.moved) {
                progress.savePosition(locator.toJSON().toString(), locator.locations.totalProgression ?: 0.0, Instant.now())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val open = open ?: return
        if (isFinishing && !reopening && container.openBook === open) closeOpenBook(container)
    }

    /**
     * Opens the reader again at the same place, for a different number of pages side by side, which Readium only takes
     * as it opens. A fresh start, not a recreation, which would bring back the old page.
     */
    private fun reopen() {
        if (reopening) return
        reopening = true
        startActivity(Intent(intent))
        finish()
    }

    /** Opens the newer version of the book that arrived while reading, at the same place. */
    private fun reload(book: PhoneBook) {
        lifecycleScope.launch {
            val latest = container.phoneBooks.get(book.id) ?: return@launch
            try {
                openReader(this@ReaderActivity, container, latest)
                finish()
            } catch (_: PhoneBookException) {
                // Carries on with the version that's open.
            }
        }
    }

    /** The status and navigation bars show with the controls, and hide while reading (a swipe brings them back). */
    private fun showSystemBars(show: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Closes the book; one just marked as read opens on its page in Carrel, with the finished sheet. */
    private fun leave(finished: Boolean) {
        val bookId = open?.book?.bookId
        if (finished && bookId != null) {
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.FINISHED_BOOK, bookId))
        }
        finish()
    }

    companion object {
        /** The PhoneBook's id. */
        const val BOOK = "book"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    open: OpenBook,
    prefs: SharedPreferences,
    look: ReaderLook,
    changeLook: (ReaderLook) -> Unit,
    /** Each theme's colours, for its swatch in the look sheet, whichever the reader is in. */
    swatchColors: (ReaderTheme) -> CarrelColors,
    /** The pages side by side the reader was opened with; changing them opens it afresh (`reopen`). */
    openedWith: ColumnCount,
    /** Readium's page turns, for the chapter and pages in the footer. */
    turns: StateFlow<PageTurn?>,
    reopen: () -> Unit,
    /** Whether it's about to open again, when places it reports aren't the reader's. */
    reopening: () -> Boolean,
    /** Opens the newer version of the book that's arrived. */
    reload: (PhoneBook) -> Unit,
    showSystemBars: (Boolean) -> Unit,
    leave: (finished: Boolean) -> Unit,
) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val scope = rememberCoroutineScope()
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var contents by rememberSaveable { mutableStateOf(false) }
    var askingRead by rememberSaveable { mutableStateOf(false) }
    var lookSheet by rememberSaveable { mutableStateOf(false) }
    var locator by remember { mutableStateOf(open.locator) }
    // Where the book opened, to tell whether the reader has started reading yet.
    val openedProgression = remember { open.locator?.locations?.totalProgression }
    var startedAt by remember { mutableStateOf(openedProgression) }
    /** Whether the book has shown its first page, after which it can be moved. */
    var laidOut by remember { mutableStateOf(false) }
    /** Where the seek bar is being dragged to (0 to 1), until the book gets there. */
    var seeking by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(locator) { seeking = null }
    /**
     * Where the reader was before changing how the text flows (its size, font, spacing, or margins), to go back to once
     * it has flowed again: Readium stays on the same page number, which is somewhere else in the text now. Kept until
     * the controls or the look sheet close, so several changes in a row all go back to the same place.
     */
    var keepPlace by remember { mutableStateOf<Locator?>(null) }
    // Unfolding a foldable, or turning the phone, changes the width.
    val columns = look.pages.columns(LocalConfiguration.current.screenWidthDp >= TWO_PAGES_WIDTH)
    var footer by remember { mutableStateOf(prefs.footer) }
    val speed = remember { ReadingSpeed(prefs) }
    /** Seconds a position, once the reader's been timed (see ReadingSpeed). */
    var secondsPerPosition by remember { mutableStateOf(speed.estimate) }
    /** How many of Readium's positions each file in the reading order has, for the time left. */
    var filePositions by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(Unit) {
        val counts = IntArray(open.publication.readingOrder.size)
        open.publication.positions().forEach { position -> open.publication.readingOrder.indexOfFirstWithHref(position.href)?.let { counts[it]++ } }
        filePositions = counts.toList()
    }
    val context = LocalContext.current
    val clock by produceState(timeNow(context)) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = timeNow(context)
        }
    }

    LaunchedEffect(controls) {
        showSystemBars(controls)
        if (!controls) keepPlace = null
    }
    LaunchedEffect(lookSheet) { if (!lookSheet) keepPlace = null }
    LaunchedEffect(navigator, colors, look) {
        val reader = navigator ?: return@LaunchedEffect
        reader.submitPreferences(readerPreferences(look, colors, openedWith))
        val place = keepPlace ?: return@LaunchedEffect
        delay(REFLOW_MS)
        // A change of margins has resized the book by now.
        reader.remeasure()
        delay(REFLOW_MS)
        reader.go(place)
    }
    LaunchedEffect(columns) { if (columns != openedWith) reopen() }
    LaunchedEffect(navigator) {
        val reader = navigator ?: return@LaunchedEffect
        /** The screen the reader was last on, to tell a page turned forward from a jump. */
        var lastTurn: PageTurn? = null
        reader.currentLocator.collect { current ->
            if (reopening()) return@collect
            locator = current
            laidOut = true
            open.locator = current
            val progression = current.locations.totalProgression
            // Readium reports the page turn as it reports the place. Going back to the place after a change to the look
            // isn't reading.
            val turn = turns.value
            val last = lastTurn
            val forward = keepPlace == null && turn != null && last != null &&
                ((turn.file == last.file && turn.screen == last.screen + 1) || (turn.file == last.file + 1 && turn.screen == 0))
            lastTurn = turn
            val positions = filePositions?.sum()
            if (progression != null && positions != null) {
                speed.arrived(progression, positions, forward)
                secondsPerPosition = speed.estimate
            }
            container.phoneBooks.savePosition(open.book.id, current.toJSON().toString(), progression)
            if (open.progress?.moved(progression) == true) container.scope.launch { open.progress.save() }
            // A copy never read here starts from its first page shown.
            if (startedAt == null) startedAt = progression
            val started = startedAt
            if (progression != null && started != null && abs(progression - started) >= SAME_PLACE) open.moved = true
            // Sent shortly after each page turn, so quitting (or the phone dying) loses nothing; flicking through
            // pages, or dragging the seek bar, sends just the last.
            val progress = open.progress
            if (open.moved && progress != null && progression != null) {
                val at = Instant.now()
                val json = current.toJSON().toString()
                open.sending?.cancel()
                open.sending = container.scope.launch {
                    delay(SEND_AFTER_MS)
                    progress.savePosition(json, progression, at)
                }
            }
        }
    }

    val turn by turns.collectAsState()
    val perScreen = if (openedWith == ColumnCount.TWO) 2 else 1
    val entries = remember { contentsEntries(open.publication) }
    var layout by remember { mutableStateOf<FileLayout?>(null) }
    /** The chapter and pages in the footer; the last stays while a new file is measured, rather than flickering. */
    var place by remember { mutableStateOf<ChapterPlace?>(null) }
    LaunchedEffect(navigator, turn?.file, turn?.screens) {
        val reader = navigator ?: return@LaunchedEffect
        val at = turn ?: return@LaunchedEffect
        layout = measureFile(reader, entries, at.file, at.screens, perScreen)
    }
    LaunchedEffect(turn, layout) {
        val at = turn ?: return@LaunchedEffect
        val measured = layout?.takeIf { it.file == at.file && it.screens == at.screens } ?: return@LaunchedEffect
        place = chapterPlace(entries, measured, at.screen * perScreen, perScreen)
    }

    val progression = locator?.locations?.totalProgression
    // While dragging the seek bar, where letting go will go, without the chapter it's leaving.
    val shownProgression = seeking ?: progression
    val chapterPlace = place.takeIf { seeking == null }
    val pagePositions = layout?.let { measured -> filePositions?.getOrNull(measured.file)?.let { it.toDouble() / measured.columns } }
    val texts = mapOf(
        FooterItem.Chapter to (place?.title ?: locator?.title).takeIf { seeking == null },
        FooterItem.Pages to chapterPlace?.let(::pagesText),
        FooterItem.Percent to shownProgression?.let { "${readPercent(it)}%" },
        FooterItem.PagesLeft to chapterPlace?.let(::pagesLeftText),
        FooterItem.ChapterTime to chapterPlace?.let { at ->
            val seconds = secondsPerPosition ?: return@let null
            pagePositions?.let { "${durationText((at.pages - at.first + 1) * it * seconds)} left in chapter" }
        },
        FooterItem.BookTime to shownProgression?.let { at ->
            val seconds = secondsPerPosition ?: return@let null
            filePositions?.let { "${durationText((1 - at) * it.sum() * seconds)} left in book" }
        },
        FooterItem.Clock to clock,
    )

    fun close() {
        scope.launch { if (open.progress?.atEnd() == true) askingRead = true else leave(false) }
    }
    BackHandler { close() }

    fun change(next: ReaderLook) {
        scope.launch {
            val reader = navigator
            if (next.reflowsFrom(look) && keepPlace == null && laidOut && reader != null) {
                keepPlace = firstWordsShown(reader) ?: reader.firstVisibleElementLocator()
            }
            changeLook(next)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.fillMaxSize()) {
            // The margins are space around the book's pages, which shows or hides the controls like the pages' middle.
            AndroidFragment<EpubNavigatorFragment>(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(interactionSource = null, indication = null) { controls = !controls }
                    .absolutePadding(
                        left = look.marginLeft.beside,
                        top = look.marginTop.aboveBelow,
                        right = look.marginRight.beside,
                        bottom = look.marginBottom.aboveBelow,
                    ),
            ) { fragment ->
                if (navigator !== fragment) {
                    // The edges turn the page; anywhere else shows or hides the controls.
                    fragment.addInputListener(DirectionalNavigationAdapter(fragment, animatedTransition = true))
                    fragment.addInputListener(object : InputListener {
                        override fun onTap(event: TapEvent): Boolean {
                            controls = !controls
                            return true
                        }
                    })
                    navigator = fragment
                }
            }
            // The footer always under the page. Always the same height, whatever the system bars are doing or it shows, or
            // showing the controls would resize the book and move the page.
            FooterRow(texts, footer.getValue(Footer.Always), Modifier.fillMaxWidth().height(FOOTER_HEIGHT).padding(horizontal = 16.dp))
        }
        val newer by open.newer.collectAsState()
        var dismissed by remember { mutableStateOf(false) }
        val elsewhere by open.elsewhere.collectAsState()
        var offerElsewhere by remember { mutableStateOf<ReadingPosition?>(null) }
        fun goTo(position: ReadingPosition) {
            scope.launch {
                val saved = runCatching { Locator.fromJSON(JSONObject(position.locator)) }.getOrNull()
                val target = saved?.takeIf { open.publication.linkWithHref(it.href) != null }
                    ?: open.publication.locateProgression(position.progression)
                    ?: return@launch
                // Once the first page has settled: a move while it's still loading is lost.
                delay(GO_SETTLE_MS)
                navigator?.go(target)
            }
        }
        LaunchedEffect(navigator, elsewhere, laidOut) {
            val position = elsewhere ?: return@LaunchedEffect
            if (navigator == null || !laidOut) return@LaunchedEffect
            open.elsewhere.value = null
            val now = locator?.locations?.totalProgression
            // Not read from here yet: straight there. Otherwise, the reader chooses.
            if (now == null || openedProgression == null || abs(now - openedProgression) < SAME_PLACE) goTo(position) else offerElsewhere = position
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            if (controls) {
                Column(Modifier.fillMaxWidth().background(colors.paperRaised).statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(::close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close the book", tint = colors.ink) }
                        Text(open.book.title, style = Carrel.type.heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    }
                    HorizontalDivider(color = colors.rule)
                }
            }
            offerElsewhere?.let { position ->
                Column(Modifier.fillMaxWidth().background(colors.paperRaised).then(if (controls) Modifier else Modifier.statusBarsPadding())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("You were at ${readPercent(position.progression)}% on another device.", style = Carrel.type.mono, color = colors.ink, modifier = Modifier.weight(1f))
                        LinkButton("Go there", {
                            offerElsewhere = null
                            goTo(position)
                        }, color = colors.accent)
                        LinkButton("Stay", { offerElsewhere = null })
                    }
                    HorizontalDivider(color = colors.rule)
                }
            }
            newer?.takeUnless { dismissed }?.let { book ->
                Column(Modifier.fillMaxWidth().background(colors.paperRaised).then(if (controls) Modifier else Modifier.statusBarsPadding())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("A newer version of this book is ready.", style = Carrel.type.mono, color = colors.ink, modifier = Modifier.weight(1f))
                        LinkButton("Reload", { reload(book) }, color = colors.accent)
                        LinkButton("Later", { dismissed = true })
                    }
                    HorizontalDivider(color = colors.rule)
                }
            }
        }
        if (controls) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.paperRaised).navigationBarsPadding()) {
                HorizontalDivider(color = colors.rule)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.ruleStrong)
                    // A round knob on a thin track, like Carrel's progress bars, rather than Material's thick one.
                    Slider(
                        value = (seeking ?: progression ?: 0.0).toFloat(),
                        onValueChange = { seeking = it.toDouble() },
                        onValueChangeFinished = {
                            seeking?.let { target ->
                                scope.launch { open.publication.locateProgression(target)?.let { navigator?.go(it) } }
                            }
                        },
                        enabled = navigator != null,
                        colors = sliderColors,
                        thumb = { Box(Modifier.size(18.dp).background(colors.accent, CircleShape)) },
                        track = { state ->
                            SliderDefaults.Track(state, Modifier.height(4.dp), colors = sliderColors, drawStopIndicator = null, thumbTrackGapSize = 0.dp)
                        },
                        modifier = Modifier.semantics { contentDescription = "Where you are in the book" },
                    )
                    FooterRow(texts, seekingPlaces(footer, seeking != null), Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinkButton("Contents", { contents = true }, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        LinkButton("Aa", { lookSheet = true }, Modifier.semantics { contentDescription = "How the book looks" }, color = colors.ink)
                    }
                }
            }
        }
    }

    if (contents) {
        ModalBottomSheet(
            onDismissRequest = { contents = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.paper,
        ) {
            val entries = remember { flatten(open.publication.tableOfContents, 0) }
            if (entries.isEmpty()) {
                Text("This book has no contents list.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
            }
            // Scrolls within the sheet rather than taking the whole screen, and never pulls the sheet up past its height.
            LazyColumn(
                Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                    .nestedScroll(KeepUpwardScroll)
                    .padding(bottom = 24.dp),
            ) {
                items(entries) { (link, depth) ->
                    Text(
                        link.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                        style = Carrel.type.body,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                navigator?.go(link)
                                contents = false
                                controls = false
                            }
                            .padding(start = (16 + depth * 16).dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }

    if (lookSheet) {
        ModalBottomSheet(
            onDismissRequest = { lookSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.paperRaised,
            // The book stays undimmed above the sheet, so each change shows as it's made.
            scrimColor = Color.Transparent,
        ) {
            // The theme, then tabs for the text, the page, and the footers (on Text each time the sheet opens).
            var tab by remember { mutableStateOf(LookTab.Text) }
            /** The footer being set up. */
            var which by remember { mutableStateOf(Footer.Controls) }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LookRow("Theme") {
                    ThemeChoices(look.theme, swatchColors) { change(look.copy(theme = it)) }
                }
                LookTabs(tab) { tab = it }
                when (tab) {
                    LookTab.Text -> {
                        LookRow("Font") {
                            FontChoices(look.font) { change(look.copy(font = it)) }
                        }
                        LookRow("Size") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                SizeButton("A−", "Smaller text", look.fontSize > MIN_FONT_SIZE, Modifier.weight(1f)) {
                                    change(look.copy(fontSize = (look.fontSize - FONT_STEP).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)))
                                }
                                Text("${(look.fontSize * 100).roundToInt()}%", style = Carrel.type.mono, color = colors.ink)
                                SizeButton("A+", "Larger text", look.fontSize < MAX_FONT_SIZE, Modifier.weight(1f)) {
                                    change(look.copy(fontSize = (look.fontSize + FONT_STEP).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)))
                                }
                            }
                        }
                        LookRow("Line spacing") {
                            Choices(Step.entries.map { it to it.spacingLabel }, look.spacing, { change(look.copy(spacing = it)) }, fill = true)
                        }
                    }
                    LookTab.Page -> {
                        LookRow("Margins") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Margin.entries.forEach { margin ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(margin.label, style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.width(64.dp))
                                        Choices(
                                            Step.entries.map { it to it.marginLabel },
                                            margin.of(look),
                                            { change(margin.set(look, it)) },
                                            Modifier.weight(1f).semantics { contentDescription = "${margin.label} margin" },
                                            fill = true,
                                        )
                                    }
                                }
                            }
                        }
                        LookRow("Pages side by side") {
                            Choices(Pages.entries.map { it to it.label }, look.pages, { change(look.copy(pages = it)) }, fill = true)
                        }
                    }
                    LookTab.Footer -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choices(Footer.entries.map { it to it.label }, which, { which = it }, fill = true)
                            Text(which.note, style = Carrel.type.mono, color = colors.inkSoft)
                        }
                        // Each item's name, and where it goes in this footer.
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FooterItem.entries.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.label, style = Carrel.type.mono, color = colors.ink, modifier = Modifier.weight(1f).padding(end = 12.dp))
                                    PlaceChoices(footer.getValue(which).getValue(item), "${item.label} ${which.label.lowercase()}") { place ->
                                        footer = footer + (which to (footer.getValue(which) + (item to place)))
                                        prefs.saveFooter(which, item, place)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (askingRead) {
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { askingRead = false },
            containerColor = colors.paperRaised,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Not the file's own title, which can be untidy ("Frankenstein; or, the modern prometheus").
                    Text("You’ve reached the end of this book. Mark it as read?", style = Carrel.type.body, color = colors.ink)
                    error?.let { FormMessage(it, Tone.Error) }
                }
            },
            confirmButton = {
                LinkButton(if (saving) "Saving…" else "Mark as read", {
                    if (!saving) {
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                open.progress?.markRead()
                                leave(true)
                            } catch (e: ApiException) {
                                error = e.message
                            } finally {
                                saving = false
                            }
                        }
                    }
                }, Modifier.padding(horizontal = 8.dp), color = colors.accent)
            },
            dismissButton = { LinkButton("Not now", { leave(false) }, Modifier.padding(horizontal = 8.dp)) },
        )
    }
}

/**
 * Has each of Readium's web views measure its width again. Readium lays its pages out to `--RS__viewportWidth`, which
 * it only measures again when the book's body changes size, and the body's width comes from that same value: so when
 * a margin changes the web views' width, the pages keep their old width (and are cut off). Setting it from the new
 * width sets Readium's own measuring going, which lines the pages up again.
 */
private fun EpubNavigatorFragment.remeasure() {
    fun webViews(view: View): Sequence<WebView> = when (view) {
        is WebView -> sequenceOf(view)
        is ViewGroup -> view.children.flatMap(::webViews)
        else -> emptySequence()
    }
    view?.let(::webViews)?.forEach { it.evaluateJavascript(REMEASURE_SCRIPT, null) }
}

private const val REMEASURE_SCRIPT =
    "document.documentElement.style.setProperty('--RS__viewportWidth', " +
        "'calc(' + Android.getViewportWidth() + 'px / ' + window.devicePixelRatio + ')')"

/** The look sheet's tabs, under the theme. */
private enum class LookTab(val label: String) { Text("Text"), Page("Page"), Footer("Footer") }

/**
 * A footer's left, centre, and right, each with its items in order, e.g. "Chapter 3 (Page 2 / 9) · 12%". The centre
 * stays in the middle. When a side's items don't fit, the chapter's title is cut short first; then, if they still
 * don't, the widest of the rest are cut short to the same width, as on the website.
 */
@Composable
private fun FooterRow(texts: Map<FooterItem, String?>, places: Map<FooterItem, FooterPlace>, modifier: Modifier) {
    val colors = Carrel.colors
    @Composable
    fun Slot(slot: FooterPlace, modifier: Modifier) {
        val items = FooterItem.entries.filter { places.getValue(it) == slot && texts[it] != null }
        Layout(
            content = {
                items.forEachIndexed { i, item ->
                    // The pages go straight after the chapter's title, as "Chapter 3 (Page 2 / 9)".
                    val before = if (i == 0) "" else if (item == FooterItem.Pages && items[i - 1] == FooterItem.Chapter) " " else " · "
                    Text(before + texts[item], style = Carrel.type.mono, color = colors.inkSoft, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            },
            modifier = modifier,
        ) { measurables, constraints ->
            val wants = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val chapter = items.indexOf(FooterItem.Chapter)
            val others = wants.filterIndexed { i, _ -> i != chapter }
            // The widest the others can be for all to fit: the narrower ones keep their width, and the rest share what's left.
            var left = constraints.maxWidth
            var widest = Int.MAX_VALUE
            others.sorted().forEachIndexed { i, want ->
                val share = left / (others.size - i)
                if (want > share && widest == Int.MAX_VALUE) widest = share
                left -= minOf(want, widest)
            }
            val widths = wants.mapIndexed { i, want -> if (i == chapter) minOf(want, left) else minOf(want, widest) }
            val placeables = measurables.mapIndexed { i, it -> it.measure(constraints.copy(minWidth = 0, maxWidth = widths[i])) }
            val width = placeables.sumOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = if (slot == FooterPlace.Right) width - placeables.sumOf { it.width } else 0
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width
                }
            }
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Slot(FooterPlace.Left, Modifier.weight(1f))
        Slot(FooterPlace.Centre, Modifier)
        Slot(FooterPlace.Right, Modifier.weight(1f))
    }
}

/** The time, to the minute, in the phone's 12- or 24-hour style. */
private fun timeNow(context: Context): String = DateFormat.getTimeFormat(context).format(Date())

/** Text, Page, and Footer, as Home's underlined tabs. */
@Composable
private fun LookTabs(tab: LookTab, onChoose: (LookTab) -> Unit) {
    val colors = Carrel.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(colors.rule, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
    ) {
        LookTab.entries.forEachIndexed { index, option ->
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

/** Each theme's name under a swatch of its own paper and ink, chosen like Choices. */
@Composable
private fun ThemeChoices(selected: ReaderTheme, swatchColors: (ReaderTheme) -> CarrelColors, onSelect: (ReaderTheme) -> Unit) {
    val colors = Carrel.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderTheme.entries.forEach { theme ->
            val chosen = theme == selected
            val swatch = swatchColors(theme)
            Column(
                Modifier
                    .weight(1f)
                    .border(1.dp, if (chosen) colors.accent else colors.ruleStrong, RoundedCornerShape(2.dp))
                    .background(if (chosen) colors.accent else Color.Transparent, RoundedCornerShape(2.dp))
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(theme) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Aa",
                    style = Carrel.type.body,
                    color = swatch.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
                        .border(1.dp, swatch.ruleStrong, RoundedCornerShape(2.dp))
                        .background(swatch.paper, RoundedCornerShape(2.dp))
                        .padding(vertical = 4.dp),
                )
                Text(theme.label, style = Carrel.type.mono.copy(letterSpacing = 0.04.em), color = if (chosen) colors.onAccent else colors.inkSoft)
            }
        }
    }
}

/** Each font's name in its own face, two to a row, chosen like Choices. */
@Composable
private fun FontChoices(selected: ReaderFont, onSelect: (ReaderFont) -> Unit) {
    val colors = Carrel.colors
    val assets = LocalContext.current.assets
    val families = remember(assets) { ReaderFont.entries.associateWith { it.labelFamily(assets) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderFont.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { font ->
                    val chosen = font == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(1.dp, if (chosen) colors.accent else colors.ruleStrong, RoundedCornerShape(2.dp))
                            .background(if (chosen) colors.accent else Color.Transparent, RoundedCornerShape(2.dp))
                            .selectable(selected = chosen, role = Role.RadioButton) { onSelect(font) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(font.label, style = Carrel.type.body.copy(fontFamily = families[font] ?: Carrel.type.body.fontFamily), color = if (chosen) colors.onAccent else colors.ink)
                    }
                }
            }
        }
    }
}

/**
 * The face a font's name is shown in, as the website does; the book's own in Carrel's Literata. OpenDyslexic's is cut
 * down to the letters of its name (Android can't read the reader's WOFF2 files), and renamed inside, as its licence
 * keeps the name OpenDyslexic for the font as published.
 */
private fun ReaderFont.labelFamily(assets: AssetManager): FontFamily? = when (this) {
    ReaderFont.Book, ReaderFont.Literata -> null
    ReaderFont.Serif -> FontFamily.Serif
    ReaderFont.Sans -> FontFamily.SansSerif
    ReaderFont.Atkinson -> FontFamily(Font("fonts/atkinson_hyperlegible.ttf", assets))
    ReaderFont.OpenDyslexic -> FontFamily(Font("fonts/reader_label.ttf", assets))
}

/** A− or A+, either side of the text's size. */
@Composable
private fun SizeButton(text: String, label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Carrel.colors
    Text(
        text,
        style = Carrel.type.mono,
        color = if (enabled) colors.ink else colors.inkFaint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .border(1.dp, colors.ruleStrong, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 10.dp),
    )
}

/** Where a footer item goes: off, or left, centre, or right, drawn as alignment icons; chosen like Choices. */
@Composable
private fun PlaceChoices(selected: FooterPlace, label: String, onSelect: (FooterPlace) -> Unit) {
    val colors = Carrel.colors
    Row(Modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FooterPlace.entries.forEach { place ->
            val chosen = place == selected
            val tint = if (chosen) colors.onAccent else colors.inkSoft
            Box(
                Modifier
                    .size(width = 40.dp, height = 36.dp)
                    .border(1.dp, if (chosen) colors.accent else colors.ruleStrong, RoundedCornerShape(2.dp))
                    .background(if (chosen) colors.accent else Color.Transparent, RoundedCornerShape(2.dp))
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(place) }
                    .semantics { contentDescription = place.label },
                contentAlignment = Alignment.Center,
            ) {
                if (place == FooterPlace.Off) Text("–", style = Carrel.type.mono, color = tint) else AlignIcon(place, tint)
            }
        }
    }
}

/** Three lines of different lengths, lined up on the left, the centre, or the right. */
@Composable
private fun AlignIcon(place: FooterPlace, color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
        val unit = size.width / 16
        listOf(14f, 8f, 11f).forEachIndexed { i, length ->
            val x = when (place) {
                FooterPlace.Right -> 15 - length
                FooterPlace.Centre -> (16 - length) / 2
                else -> 1f
            }
            val y = (2 + i * 4) * unit
            drawLine(color, Offset(x * unit, y), Offset((x + length) * unit, y), 1.5f * unit, StrokeCap.Round)
        }
    }
}

/** One of the reader's settings in the look sheet: its name, and its choices. */
@Composable
private fun LookRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        content()
    }
}

/**
 * Keeps scrolling up past the end of a list in a bottom sheet from reaching the sheet, which would stretch it past its
 * height and spring back. Scrolling down from the top still reaches it, to swipe the sheet away.
 */
private object KeepUpwardScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
        if (available.y < 0) available.copy(x = 0f) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
        if (available.y < 0) available.copy(x = 0f) else Velocity.Zero
}

/** The contents list's entries in reading order, each with how deeply it's nested. */
internal fun flatten(links: List<Link>, depth: Int): List<Pair<Link, Int>> =
    links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

/**
 * The first words on the page, as a place Readium finds again by its words (with a little either side to tell them
 * apart) however the text flows; null on a page without any, such as a picture. Readium's own first visible paragraph
 * can be the last one on the page before, and goes back to where that paragraph starts.
 */
private suspend fun firstWordsShown(navigator: EpubNavigatorFragment): Locator? {
    val script = """
        (function() {
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
            for (var node = walker.nextNode(); node; node = walker.nextNode()) {
                if (!node.textContent.trim()) continue;
                var range = document.createRange();
                range.selectNodeContents(node);
                // The first line starting on this page (the width is rounded, hence the pixel to spare).
                var line = Array.prototype.find.call(range.getClientRects(), function(r) {
                    return r.width > 0 && r.left > -1 && r.left < window.innerWidth - 1;
                });
                if (!line) continue;
                var caret = document.caretRangeFromPoint(line.left + 1, line.top + line.height / 2);
                var start = caret && caret.startContainer === node ? caret.startOffset : 0;
                var text = node.textContent;
                return { before: text.slice(Math.max(0, start - 32), start), highlight: text.slice(start, start + 64), after: text.slice(start + 64, start + 96) };
            }
            return null;
        })();
    """.trimIndent()
    val found = try {
        navigator.evaluateJavascript(script)?.takeIf { it != "null" }?.let(::JSONObject)
    } catch (_: JSONException) {
        null
    } ?: return null
    return navigator.currentLocator.value.copy(
        text = Locator.Text(before = found.optString("before"), highlight = found.optString("highlight"), after = found.optString("after")),
    )
}

/**
 * Two pages side by side. Readium's own stylesheet only splits the page from 960 CSS pixels wide, wider than an unfolded
 * foldable or a phone on its side, so its column settings are set outright: two narrow columns fill the width. As in
 * Readium's own two columns there's no gap (each page has its own margins), or every page turn would drift by it.
 */
private fun layoutFor(columns: ColumnCount) =
    if (columns == ColumnCount.TWO) RsProperties(colCount = ColCount.TWO, colWidth = Length.Em(10.0)) else RsProperties()

/** A page turn in Readium: the file on screen (its place in the reading order), the screen showing, and how many it has. */
private data class PageTurn(val file: Int, val screen: Int, val screens: Int)

private val FOOTER_HEIGHT = 24.dp

/** Wide enough for two pages side by side, in dp: an unfolded foldable, a tablet, or a phone on its side. */
private const val TWO_PAGES_WIDTH = 600

/** How long the first page is given to settle before going to a place saved on another device. */
private const val GO_SETTLE_MS = 500L
/** How long the text is given to flow again at a new size before going back to where the reader was. */
private const val REFLOW_MS = 300L
/** How long after a page turn the place is sent to Carrel. */
private const val SEND_AFTER_MS = 2_000L
