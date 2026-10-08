package uk.co.zenithal.carrel.ui.reading

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.Grouping
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.PhoneBook
import uk.co.zenithal.carrel.data.PhoneBookException
import uk.co.zenithal.carrel.data.grouped
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.reader.bookToResume
import uk.co.zenithal.carrel.reader.openReader
import uk.co.zenithal.carrel.reader.readPercent
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.components.shelfEdge
import uk.co.zenithal.carrel.ui.library.Choices
import uk.co.zenithal.carrel.ui.library.underRule
import uk.co.zenithal.carrel.ui.theme.Carrel

private const val EPUB = "application/epub+zip"
/** Books shown in the Last read row; See all shows the rest. */
private const val SHELF_BOOKS = 12

/**
 * Reading in Carrel, from the Home button: the books on this phone (Last read), to read, add to, and browse in full
 * (`seeAll`), then each online library's newest books. With `resume`, it first opens the book the reader was last
 * reading, if they haven't finished it, so closing that book comes back here. `addBook` copies in a picked EPUB;
 * `linkBook` links one to a book in Carrel.
 */
@Composable
fun ReadingScreen(
    resume: Boolean,
    addBook: (Uri) -> Unit,
    openBook: (path: String) -> Unit,
    linkBook: (phoneBookId: Long) -> Unit,
    seeAll: () -> Unit,
    /** An online library's catalogue, at its start or `url`. */
    openCatalogue: (libraryId: Long, url: String?) -> Unit,
    /** The form to add an online library (null) or change one. */
    editLibrary: (libraryId: Long?) -> Unit,
    /** Linking a book just downloaded, then reading it. */
    linkThenRead: (phoneBookId: Long) -> Unit,
) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val books = container.phoneBooks.all.collectAsStateWithLifecycle(null).value
    val read = rememberOpenReader()
    var choosing by remember { mutableStateOf<PhoneBook?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(addBook) }
    // Only as the screen first opens, not each time the reader comes back to it.
    var resumed by rememberSaveable { mutableStateOf(!resume) }
    LaunchedEffect(Unit) {
        if (resumed) return@LaunchedEffect
        resumed = true
        val signedIn = container.session.state.value is Session.SignedIn
        val library = if (signedIn) container.store.saved(LIBRARY_PATH, LibrarySerializer).first() else null
        bookToResume(container.phoneBooks.lastOpened, container.phoneBooks.all.first(), library)?.let(read.open)
    }

    Column(Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text("Your books", style = Carrel.type.displayLarge, color = colors.ink)
        if (books == null) return@Column
        Column(Modifier.padding(top = 40.dp)) {
            Row(Modifier.fillMaxWidth().underRule(colors.rule).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last read (${books.size})".uppercase(),
                    style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
                    color = colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                if (books.isNotEmpty()) {
                    Text(
                        "SEE ALL →",
                        style = Carrel.type.mono.copy(letterSpacing = 0.1.em),
                        color = colors.accent,
                        modifier = Modifier.clickable(onClickLabel = "See all your books", role = Role.Button, onClick = seeAll),
                    )
                }
            }
            if (books.isEmpty()) {
                Text(
                    "Add a DRM-free EPUB from your phone to read it here. Link it to its book in Carrel, and reading it keeps your progress up to date.",
                    style = Carrel.type.body,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                LazyRow(
                    Modifier.padding(top = 18.dp).fillMaxWidth().shelfEdge(colors.ruleStrong),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(books.take(SHELF_BOOKS), key = { it.id }) { book -> ShelfCover(book, { read.open(book) }) { choosing = book } }
                }
            }
            LinkButton("Add a book from your phone", { pick.launch(arrayOf(EPUB)) }, Modifier.padding(top = 18.dp), color = colors.accent)
            OpenReaderStatus(read, Modifier.padding(top = 12.dp))
        }
        OnlineShelves(read, openCatalogue, editLibrary, linkThenRead)
    }

    choosing?.let { book -> BookSheet(book, read, openBook, linkBook) { choosing = null } }
}

/**
 * Every book on this phone, grouped by how the reader chooses (most recently read, title, author, or series), which is
 * remembered.
 */
@Composable
fun PhoneBooksScreen(openBook: (path: String) -> Unit, linkBook: (phoneBookId: Long) -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val prefs = LocalContext.current.getSharedPreferences(READING_PREFS, Context.MODE_PRIVATE)
    var grouping by remember { mutableStateOf(Grouping.entries.firstOrNull { it.name == prefs.getString(GROUPING, null) } ?: Grouping.Recent) }
    val books = container.phoneBooks.all.collectAsStateWithLifecycle(null).value
    val read = rememberOpenReader()
    var choosing by remember { mutableStateOf<PhoneBook?>(null) }

    Column(Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text("On this phone", style = Carrel.type.displayLarge, color = colors.ink)
        if (books == null) return@Column
        Text("${books.size} ${if (books.size == 1) "book" else "books"}", style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
        Choices(Grouping.entries.map { it to it.label }, grouping, { choice ->
            grouping = choice
            prefs.edit { putString(GROUPING, choice.name) }
        }, Modifier.padding(top = 24.dp))
        OpenReaderStatus(read, Modifier.padding(top = 12.dp))
        grouped(books, grouping).forEach { (heading, group) ->
            if (heading != null) {
                Text(
                    heading.uppercase(),
                    style = Carrel.type.monoMedium.copy(letterSpacing = 0.14.em),
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = 32.dp).fillMaxWidth().underRule(colors.rule).padding(bottom = 10.dp),
                )
            }
            Column(Modifier.padding(top = if (heading == null) 16.dp else 0.dp)) {
                group.forEach { book -> BookRow(book, grouping == Grouping.Series, { read.open(book) }) { choosing = book } }
            }
        }
    }

    choosing?.let { book -> BookSheet(book, read, openBook, linkBook) { choosing = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCover(book: PhoneBook, onRead: () -> Unit, onMore: () -> Unit) {
    val container = LocalContainer.current
    Column(
        Modifier
            .width(92.dp)
            .readOrMore(onRead, onMore)
            .semantics(mergeDescendants = true) { contentDescription = book.title },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Cover(container.phoneBooks.coverUrl(book), book.title, book.authorList.firstOrNull(), 92.dp)
        Text(progressLabel(book), style = Carrel.type.mono, color = Carrel.colors.inkSoft)
    }
}

/** A book in the full list: cover, title, authors, and how far through; the series number when grouped by series. */
@Composable
private fun BookRow(book: PhoneBook, showPosition: Boolean, onRead: () -> Unit, onMore: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    Row(
        Modifier.fillMaxWidth().readOrMore(onRead, onMore).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(container.phoneBooks.coverUrl(book), book.title, book.authorList.firstOrNull(), 56.dp)
        Column(Modifier.weight(1f)) {
            Text(book.title, style = Carrel.type.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.authorList.isNotEmpty()) {
                Text(listNames(book.authorList), style = Carrel.type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val position = seriesPosition(book.seriesPosition)?.takeIf { showPosition }?.let { "Book $it" }
            Text(listOfNotNull(position, progressLabel(book)).joinToString(" · "), style = Carrel.type.mono, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Tap to read; touch and hold for the book's link and removing it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.readOrMore(onRead: () -> Unit, onMore: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClickLabel = "Read",
        onLongClickLabel = "More",
        role = Role.Button,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMore()
        },
        onClick = onRead,
    )
}

private fun progressLabel(book: PhoneBook) = book.progression?.let { "${readPercent(it)}%" } ?: "New"

/** A book touched and held: reading it, its Carrel book (or linking one), and removing it from the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSheet(book: PhoneBook, read: OpenReader, openBook: (String) -> Unit, linkBook: (Long) -> Unit, onClose: () -> Unit) {
    val colors = Carrel.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var confirmingRemove by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text(book.title, style = Carrel.type.heading, color = colors.ink)
                if (book.authorList.isNotEmpty()) {
                    Text(listNames(book.authorList), style = Carrel.type.body, color = colors.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            PrimaryButton("Read", {
                onClose()
                read.open(book)
            })
            val bookId = book.bookId
            if (bookId != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkButton("Open its page in Carrel", {
                        onClose()
                        openBook("/books/$bookId")
                    }, color = colors.ink)
                    LinkButton("Link it to a different book", {
                        onClose()
                        linkBook(book.id)
                    })
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Not linked to a book in Carrel, so reading it doesn’t update your library.",
                        style = Carrel.type.body.copy(fontStyle = FontStyle.Italic),
                        color = colors.inkSoft,
                    )
                    LinkButton("Link it to a book in Carrel", {
                        onClose()
                        linkBook(book.id)
                    }, color = colors.ink)
                }
            }
            LinkButton("Remove from this phone", { confirmingRemove = true }, color = colors.danger)
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            containerColor = colors.paperRaised,
            text = {
                Text(
                    "Remove this book from your phone? If it’s in your library in Carrel, it stays there, with your progress.",
                    style = Carrel.type.body,
                    color = colors.ink,
                )
            },
            confirmButton = {
                LinkButton("Remove", {
                    confirmingRemove = false
                    scope.launch { container.phoneBooks.remove(book) }
                    onClose()
                }, Modifier.padding(horizontal = 8.dp), color = colors.danger)
            },
            dismissButton = { LinkButton("Cancel", { confirmingRemove = false }, Modifier.padding(horizontal = 8.dp)) },
        )
    }
}

/** On a book's page, when it's on this phone: a button to read it, and how far through it is. */
@Composable
fun ReadOnPhone(bookId: Long) {
    val container = LocalContainer.current
    val book = container.phoneBooks.all.collectAsStateWithLifecycle(null).value?.firstOrNull { it.bookId == bookId } ?: return
    val read = rememberOpenReader()
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Read", { read.open(book) })
            Text(
                "On this phone" + (book.progression?.let { " · ${readPercent(it)}%" } ?: ""),
                style = Carrel.type.mono,
                color = Carrel.colors.inkSoft,
            )
        }
        OpenReaderStatus(read)
    }
}

/** Opens books in the reader, with any problem opening one to show. */
internal class OpenReader(val open: (PhoneBook) -> Unit, val error: String?)

@Composable
internal fun rememberOpenReader(): OpenReader {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    return OpenReader({ book ->
        if (!opening) {
            scope.launch {
                opening = true
                error = null
                try {
                    openReader(context, container, book)
                } catch (e: PhoneBookException) {
                    error = e.message
                } finally {
                    opening = false
                }
            }
        }
    }, error)
}

/** A problem opening a book. */
@Composable
internal fun OpenReaderStatus(read: OpenReader, modifier: Modifier = Modifier) {
    read.error?.let { FormMessage(it, Tone.Error, modifier) }
}

private const val READING_PREFS = "reading"
private const val GROUPING = "grouping"
