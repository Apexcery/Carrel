package uk.co.zenithal.carrel.ui.reading

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.Download
import uk.co.zenithal.carrel.data.Library
import uk.co.zenithal.carrel.data.LibraryException
import uk.co.zenithal.carrel.data.OnlineBook
import uk.co.zenithal.carrel.data.OnlineFeed
import uk.co.zenithal.carrel.data.PhoneBookException
import uk.co.zenithal.carrel.data.Section
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.newestSection
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.components.shelfEdge
import uk.co.zenithal.carrel.ui.library.underRule
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Books shown in each library's row of newest books. */
private const val ONLINE_SHELF_BOOKS = 12

/**
 * Each online library's newest books, as a row of covers, opening its whole catalogue (`openCatalogue`); and adding a
 * library (`editLibrary` with null). A library that can't be reached says so without holding up the rest.
 */
@Composable
internal fun OnlineShelves(read: OpenReader, openCatalogue: (Long, String?) -> Unit, editLibrary: (Long?) -> Unit, linkThenRead: (Long) -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val libraries = container.libraries.all.collectAsStateWithLifecycle(null).value ?: return
    var attempt by remember { mutableIntStateOf(0) }
    if (libraries.isNotEmpty()) AskForLocalNetwork { attempt++ }
    var choosing by remember { mutableStateOf<Pair<Library, OnlineBook>?>(null) }

    Column(Modifier.padding(top = 48.dp)) {
        SectionTitle("Online books")
        if (libraries.isEmpty()) {
            Text(
                "Add an online library, such as Calibre’s content server or Calibre-Web, to browse its books and download them to read here.",
                style = Carrel.type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        libraries.forEach { library ->
            NewestRow(library, attempt, { openCatalogue(library.id, null) }) { book -> choosing = library to book }
        }
        LinkButton("Add an online library", { editLibrary(null) }, Modifier.padding(top = 18.dp), color = colors.accent)
    }

    choosing?.let { (library, book) -> OnlineBookSheet(library, book, read, linkThenRead) { choosing = null } }
}

@Composable
private fun NewestRow(library: Library, attempt: Int, seeAll: () -> Unit, choose: (OnlineBook) -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    var books by remember(library.id) { mutableStateOf<List<OnlineBook>?>(null) }
    var error by remember(library.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(library, attempt, retry) {
        error = null
        try {
            val root = container.libraries.feed(library, fresh = attempt > 0 || retry > 0)
            books = newestSection(root.sections)?.let { container.libraries.feed(library, it.url, fresh = attempt > 0 || retry > 0).books } ?: root.books
        } catch (e: LibraryException) {
            error = e.message
        }
    }
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth().underRule(colors.rule).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(library.name.uppercase(), style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em), color = colors.inkSoft, modifier = Modifier.weight(1f))
            Text(
                "SEE ALL →",
                style = Carrel.type.mono.copy(letterSpacing = 0.1.em),
                color = colors.accent,
                modifier = Modifier.clickable(onClickLabel = "Browse ${library.name}", role = Role.Button, onClick = seeAll),
            )
        }
        val shown = books
        when {
            error != null -> LibraryError(error.orEmpty(), Modifier.padding(top = 14.dp)) { retry++ }
            shown == null -> Text("Loading…", style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 14.dp))
            shown.isEmpty() -> Text("No books here yet.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 14.dp))
            else -> LazyRow(
                Modifier.padding(top = 18.dp).fillMaxWidth().shelfEdge(colors.ruleStrong),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shown.take(ONLINE_SHELF_BOOKS), key = { it.id }) { book ->
                    Column(
                        Modifier
                            .width(92.dp)
                            .clickable(onClickLabel = "More", role = Role.Button) { choose(book) }
                            .semantics(mergeDescendants = true) { contentDescription = book.title },
                    ) {
                        Cover(book.thumbnail, book.title, book.authors.firstOrNull(), 92.dp, imageLoader = container.libraryImages)
                    }
                }
            }
        }
    }
}

/**
 * An online library's catalogue: at its start (`url` null) or a section, search results, or more of a list. Sections
 * open further pages (`openPage`); books open their sheet, to download and read.
 */
@Composable
fun CatalogueScreen(libraryId: Long, url: String?, openPage: (String) -> Unit, editLibrary: () -> Unit, linkThenRead: (Long) -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    var library by remember { mutableStateOf<Library?>(null) }
    var feed by remember { mutableStateOf<OnlineFeed?>(null) }
    // Further pages, added by Load more: a long list of sections (series, authors) or books.
    val more = remember { mutableStateListOf<OnlineBook>() }
    val moreSections = remember { mutableStateListOf<Section>() }
    var next by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var words by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf<OnlineBook?>(null) }
    val scope = rememberCoroutineScope()
    val read = rememberOpenReader()
    AskForLocalNetwork { attempt++ }

    LaunchedEffect(libraryId, url, attempt) {
        error = null
        try {
            val found = container.libraries.get(libraryId) ?: return@LaunchedEffect
            library = found
            container.libraries.feed(found, url ?: found.url, fresh = attempt > 0).let {
                feed = it
                more.clear()
                moreSections.clear()
                next = it.next
            }
        } catch (e: LibraryException) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize().background(colors.paper).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        val current = library ?: return@Column
        Kicker(current.name)
        val page = feed
        Text(page?.title ?: current.name, style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
        if (url == null) LinkButton("Change this library’s details", editLibrary, Modifier.padding(top = 8.dp))
        when {
            error != null -> LibraryError(error.orEmpty(), Modifier.padding(top = 24.dp)) { attempt++ }
            page == null -> Text("Loading…", style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 24.dp))
            else -> {
                if (page.search != null) {
                    Field(
                        "Search this library",
                        words,
                        { words = it },
                        Modifier.padding(top = 24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onDone = { container.libraries.searchUrl(page, words)?.takeIf { words.isNotBlank() }?.let(openPage) },
                    )
                }
                OpenReaderStatus(read, Modifier.padding(top = 12.dp))
                val sections = page.sections + moreSections
                if (sections.isNotEmpty()) {
                    Column(Modifier.padding(top = 16.dp)) {
                        sections.forEach { section ->
                            Text(
                                "${section.title} →",
                                style = Carrel.type.body,
                                color = colors.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { openPage(section.url) }
                                    .underRule(colors.rule)
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                }
                val books = page.books + more
                if (sections.isEmpty() && books.isEmpty()) {
                    Text("No books here.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 24.dp))
                }
                Column(Modifier.padding(top = 8.dp)) {
                    books.forEach { book -> OnlineBookRow(book, current) { choosing = book } }
                }
                next?.let { nextUrl ->
                    LinkButton(if (loadingMore) "Loading…" else "Load more", {
                        if (!loadingMore) {
                            scope.launch {
                                loadingMore = true
                                try {
                                    container.libraries.feed(current, nextUrl).let {
                                        moreSections.addAll(it.sections)
                                        more.addAll(it.books)
                                        next = it.next
                                    }
                                } catch (e: LibraryException) {
                                    error = e.message
                                } finally {
                                    loadingMore = false
                                }
                            }
                        }
                    }, Modifier.padding(top = 16.dp), color = colors.ink)
                }
            }
        }
    }

    choosing?.let { book -> library?.let { OnlineBookSheet(it, book, read, linkThenRead) { choosing = null } } }
}

/** A book in a catalogue: its cover, title, authors, and series, and whether it's on the phone. */
@Composable
private fun OnlineBookRow(book: OnlineBook, library: Library, onChoose: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val onPhone = container.phoneBooks.all.collectAsStateWithLifecycle(null).value?.any { it.libraryId == library.id && it.onlineId == book.id } == true
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "More", role = Role.Button, onClick = onChoose).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(book.thumbnail, book.title, book.authors.firstOrNull(), 56.dp, imageLoader = container.libraryImages)
        Column(Modifier.weight(1f)) {
            Text(book.title, style = Carrel.type.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.authors.isNotEmpty()) {
                Text(listNames(book.authors), style = Carrel.type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val details = listOfNotNull(seriesLabel(book), "On this phone".takeIf { onPhone }).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun seriesLabel(book: OnlineBook) =
    book.series?.let { series -> listOfNotNull(seriesPosition(book.seriesPosition)?.let { "Book $it" }, series).joinToString(" · ") }

/**
 * A book in an online library: its cover, details, and summary, and reading it. The first time, it's downloaded to the
 * phone to read offline (then offered a link to its book in Carrel); after that, opening it fetches any newer version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlineBookSheet(library: Library, book: OnlineBook, read: OpenReader, linkThenRead: (Long) -> Unit, onClose: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val scope = rememberCoroutineScope()
    val copy = container.phoneBooks.all.collectAsStateWithLifecycle(null).value?.firstOrNull { it.libraryId == library.id && it.onlineId == book.id }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.paper) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(book.cover ?: book.thumbnail, book.title, book.authors.firstOrNull(), 96.dp, imageLoader = container.libraryImages)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, style = Carrel.type.heading, color = colors.ink)
                    if (book.authors.isNotEmpty()) Text(listNames(book.authors), style = Carrel.type.body, color = colors.inkSoft)
                    seriesLabel(book)?.let { Text(it, style = Carrel.type.mono, color = colors.inkSoft) }
                }
            }
            val url = book.epub
            when {
                copy != null -> PrimaryButton("Read", {
                    onClose()
                    read.open(copy)
                })
                url == null -> Text("This book isn’t available as an EPUB, so Carrel can’t read it.", style = Carrel.type.body, color = colors.inkSoft)
                else -> PrimaryButton(if (downloading) "Downloading…" else "Download and read", {
                    if (!downloading) {
                        scope.launch {
                            downloading = true
                            error = null
                            try {
                                val download = container.libraries.download(url, container.phoneBooks.newFile(), null, null)
                                if (download is Download.Fetched) {
                                    val added = container.phoneBooks.addDownload(download, library, book, url)
                                    onClose()
                                    if (added.bookId == null) linkThenRead(added.id) else read.open(added)
                                }
                            } catch (e: LibraryException) {
                                error = e.message
                            } catch (e: PhoneBookException) {
                                error = e.message
                            } finally {
                                downloading = false
                            }
                        }
                    }
                }, enabled = !downloading)
            }
            error?.let { FormMessage(it, Tone.Error) }
            book.summary?.let { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    summary.split("\n\n").filter { it.isNotBlank() }.forEach { Text(it.trim(), style = Carrel.type.body, color = colors.ink) }
                }
            }
        }
    }
}

/**
 * Adding an online library (`libraryId` null) or changing or removing one. Saving checks the catalogue answers with
 * these details first.
 */
@Composable
fun LibraryFormScreen(libraryId: Long?, onSaved: () -> Unit, onRemoved: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf<Library?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingRemove by remember { mutableStateOf(false) }
    AskForLocalNetwork {}

    LaunchedEffect(libraryId) {
        val found = libraryId?.let { container.libraries.get(it) } ?: return@LaunchedEffect
        existing = found
        name = found.name
        address = found.url
        username = found.username.orEmpty()
        password = container.libraries.password(found).orEmpty()
    }

    fun save() {
        if (saving) return
        if (address.isBlank()) {
            error = "Enter the catalogue’s address."
            return
        }
        scope.launch {
            saving = true
            error = null
            try {
                val old = libraryId?.let { container.libraries.get(it) }
                val saved = container.libraries.save(libraryId, name, address, username, password)
                old?.let { container.phoneBooks.moveLibrary(it.id, it.url, saved.url) }
                onSaved()
            } catch (e: LibraryException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.paper).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Kicker("Online library")
            Text(if (libraryId == null) "Add a library" else "Change a library", style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Any OPDS catalogue works, such as Calibre’s content server or Calibre-Web. Its address usually ends in /opds.",
                style = Carrel.type.body,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Field("Name", name, { name = it })
        Field("Address", address, { address = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (address.trim().startsWith("http://", ignoreCase = true) || (address.isNotBlank() && "://" !in address)) {
            Text(
                "This address isn’t encrypted (http), so your login and books travel as they are. That’s fine on your home network.",
                style = Carrel.type.mono,
                color = colors.inkSoft,
            )
        }
        Field("Username (if it asks for one)", username, { username = it })
        Field("Password", password, { password = it }, password = true, onDone = ::save)
        error?.let { FormMessage(it, Tone.Error) }
        PrimaryButton(if (saving) "Checking…" else "Save", ::save, enabled = !saving)
        existing?.let { LinkButton("Remove this library", { confirmingRemove = true }, color = colors.danger) }
    }

    if (confirmingRemove) {
        val library = existing ?: return
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            containerColor = colors.paperRaised,
            text = {
                Text(
                    "Remove this library? Books you’ve downloaded from it stay on your phone, but won’t get newer versions.",
                    style = Carrel.type.body,
                    color = colors.ink,
                )
            },
            confirmButton = {
                LinkButton("Remove", {
                    confirmingRemove = false
                    scope.launch {
                        container.libraries.remove(library)
                        container.phoneBooks.forgetLibrary(library.id)
                        onRemoved()
                    }
                }, Modifier.padding(horizontal = 8.dp), color = colors.danger)
            },
            dismissButton = { LinkButton("Cancel", { confirmingRemove = false }, Modifier.padding(horizontal = 8.dp)) },
        )
    }
}

/**
 * A library that couldn't be reached. When Android hasn't let Carrel onto the home network (from Android 17), it says
 * so, with the way to allow it.
 */
@Composable
private fun LibraryError(message: String, modifier: Modifier = Modifier, retry: () -> Unit) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!hasLocalNetwork(context)) {
            FormMessage("Carrel isn’t allowed on your home network, which libraries like Calibre’s server need.", Tone.Error)
            LinkButton("Allow it in Settings", {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }, color = Carrel.colors.ink)
        } else {
            FormMessage(message, Tone.Error)
        }
        LinkButton("Try again", retry)
    }
}

private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Whether Carrel may reach the home network: always before Android 17, which made it a permission. */
private fun hasLocalNetwork(context: android.content.Context) =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK || ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

private const val LOCAL_NETWORK_SDK = 37

/** Only asked once a session; after that, a library that can't be reached explains (see LibraryError). */
private var askedForLocalNetwork = false

/**
 * Asks to reach the home network, the first time an online library is shown or added (from Android 17). `onAnswer`
 * runs once the reader has answered, to try again.
 */
@Composable
private fun AskForLocalNetwork(onAnswer: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onAnswer() }
    LaunchedEffect(Unit) {
        if (!askedForLocalNetwork && !hasLocalNetwork(context)) {
            askedForLocalNetwork = true
            launcher.launch(LOCAL_NETWORK)
        }
    }
}
