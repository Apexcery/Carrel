@file:OptIn(ExperimentalReadiumApi::class)

package uk.co.zenithal.carrel.reader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
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
import uk.co.zenithal.carrel.ui.theme.darkColors
import uk.co.zenithal.carrel.ui.theme.lightColors
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.epub.css.ColCount
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.shared.publication.services.locateProgression
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.ui.library.Choices

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
 * Reads an EPUB from the phone with Readium, full screen. Tapping the middle shows the controls (contents, text size,
 * and pages side by side); tapping the edges or swiping turns the page. Colours follow the app's theme.
 */
class ReaderActivity : FragmentActivity() {
    private lateinit var container: AppContainer
    private var open: OpenBook? = null
    /** Closing only to open again (see [reopen]), so the book stays open, and where it was is kept. */
    private var reopening = false
    private lateinit var prefs: SharedPreferences
    private lateinit var columns: ColumnCount

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
        // Fixed while the reader is open; a change of pages side by side opens it afresh (see reopen).
        val columns = prefs.pages.columns(resources.configuration.screenWidthDp >= TWO_PAGES_WIDTH).also { columns = it }
        supportFragmentManager.fragmentFactory = EpubNavigatorFactory(open.publication).createFragmentFactory(
            initialLocator = open.locator,
            initialPreferences = readerPreferences(if (dark) darkColors(accent) else lightColors(accent), prefs.fontSize, columns),
            configuration = EpubNavigatorFragment.Configuration(readiumCssRsProperties = layoutFor(columns)),
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
            DisposableEffect(darkNow) {
                edgeToEdge(darkNow)
                onDispose {}
            }
            CompositionLocalProvider(LocalContainer provides container) {
                CarrelTheme(darkNow, chosenAccent) {
                    ReaderScreen(open, prefs, columns, ::reopen, { reopening }, ::reload, ::showSystemBars, ::leave)
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
        if (open != null && prefs.pages.columns(newConfig.screenWidthDp >= TWO_PAGES_WIDTH) != columns) reopen()
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
    /** The pages side by side the reader was opened with; changing them opens it afresh (`reopen`). */
    openedWith: ColumnCount,
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
    var fontSize by remember { mutableDoubleStateOf(prefs.fontSize) }
    var pages by remember { mutableStateOf(prefs.pages) }
    var locator by remember { mutableStateOf(open.locator) }
    // Where the book opened, to tell whether the reader has started reading yet.
    val openedProgression = remember { open.locator?.locations?.totalProgression }
    var startedAt by remember { mutableStateOf(openedProgression) }
    /** Whether the book has shown its first page, after which it can be moved. */
    var laidOut by remember { mutableStateOf(false) }
    /** Where the seek bar is being dragged to (0 to 1), until the book gets there. */
    var seeking by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(locator) { seeking = null }
    // Unfolding a foldable, or turning the phone, changes the width.
    val columns = pages.columns(LocalConfiguration.current.screenWidthDp >= TWO_PAGES_WIDTH)

    LaunchedEffect(controls) { showSystemBars(controls) }
    LaunchedEffect(navigator, colors, fontSize) { navigator?.submitPreferences(readerPreferences(colors, fontSize, openedWith)) }
    LaunchedEffect(columns) { if (columns != openedWith) reopen() }
    LaunchedEffect(navigator) {
        val reader = navigator ?: return@LaunchedEffect
        reader.currentLocator.collect { current ->
            if (reopening()) return@collect
            locator = current
            laidOut = true
            open.locator = current
            val progression = current.locations.totalProgression
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

    fun close() {
        scope.launch { if (open.progress?.atEnd() == true) askingRead = true else leave(false) }
    }
    BackHandler { close() }

    fun changeSize(by: Double) {
        fontSize = (fontSize + by).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        prefs.edit { putFloat(FONT_SIZE, fontSize.toFloat()) }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        AndroidFragment<EpubNavigatorFragment>(Modifier.fillMaxSize()) { fragment ->
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
                    val progression = locator?.locations?.totalProgression
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
                    Text(
                        // While dragging, where letting go will go, without the chapter it's leaving.
                        seeking?.let { "${readPercent(it)}%" }
                            ?: listOfNotNull(progression?.let { "${readPercent(it)}%" }, locator?.title).joinToString(" · "),
                        style = Carrel.type.mono,
                        color = colors.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Choices(Pages.entries.map { it to it.label }, pages, { choice ->
                        pages = choice
                        prefs.edit { putString(PAGES, choice.name) }
                    })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LinkButton("Contents", { contents = true }, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        LinkButton("A−", { changeSize(-FONT_STEP) }, Modifier.semantics { contentDescription = "Smaller text" }, color = colors.ink)
                        LinkButton("A+", { changeSize(FONT_STEP) }, Modifier.semantics { contentDescription = "Larger text" }, color = colors.ink)
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
private fun flatten(links: List<Link>, depth: Int): List<Pair<Link, Int>> =
    links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

/** The book in Carrel's paper and ink, at the reader's text size (1 is the book's own), in one or two columns. */
private fun readerPreferences(colors: CarrelColors, fontSize: Double, columns: ColumnCount) = EpubPreferences(
    backgroundColor = ReadiumColor(colors.paper.toArgb()),
    columnCount = columns,
    textColor = ReadiumColor(colors.ink.toArgb()),
    fontSize = fontSize,
)

/**
 * Two pages side by side. Readium's own stylesheet only splits the page from 960 CSS pixels wide, wider than an unfolded
 * foldable or a phone on its side, so its column settings are set outright: two narrow columns fill the width. As in
 * Readium's own two columns there's no gap (each page has its own margins), or every page turn would drift by it.
 */
private fun layoutFor(columns: ColumnCount) =
    if (columns == ColumnCount.TWO) RsProperties(colCount = ColCount.TWO, colWidth = Length.Em(10.0)) else RsProperties()

/** Pages side by side: automatically when the screen is wide (unfolded, a tablet, or landscape), or always one or two. */
private enum class Pages(val label: String) {
    Auto("Auto"), One("One page"), Two("Two pages");

    fun columns(wide: Boolean) = when (this) {
        Auto -> if (wide) ColumnCount.TWO else ColumnCount.ONE
        One -> ColumnCount.ONE
        Two -> ColumnCount.TWO
    }
}

private val SharedPreferences.fontSize get() = getFloat(FONT_SIZE, 1f).toDouble()
private val SharedPreferences.pages get() = Pages.entries.firstOrNull { it.name == getString(PAGES, null) } ?: Pages.Auto

/** Wide enough for two pages side by side, in dp: an unfolded foldable, a tablet, or a phone on its side. */
private const val TWO_PAGES_WIDTH = 600
private const val PAGES = "pages"

/** How long the first page is given to settle before going to a place saved on another device. */
private const val GO_SETTLE_MS = 500L
/** How long after a page turn the place is sent to Carrel. */
private const val SEND_AFTER_MS = 2_000L

private const val FONT_SIZE = "fontSize"
private const val FONT_STEP = 0.1
private const val MIN_FONT_SIZE = 0.7
private const val MAX_FONT_SIZE = 2.0
