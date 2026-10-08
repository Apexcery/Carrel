package uk.co.zenithal.carrel.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookSearchResponse
import uk.co.zenithal.carrel.data.BookSearchResult
import uk.co.zenithal.carrel.data.ChooseBookRequest
import uk.co.zenithal.carrel.data.ImportMatch
import uk.co.zenithal.carrel.data.ImportReviewItem
import uk.co.zenithal.carrel.data.ImportState
import uk.co.zenithal.carrel.data.ImportStatus
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.formatCount
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.pickerQuery
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.data.splitSeries
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SecondaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.library.Choices
import uk.co.zenithal.carrel.ui.library.FieldLabel
import uk.co.zenithal.carrel.ui.library.ProgressBar
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import java.io.IOException
import java.time.LocalDate

private const val POLL_MS = 3000L
private const val PICKER_RESULTS = 5

/** What an export file might be called by the app it's picked from: CSVs often go as plain text, or not typed at all. */
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream")

/**
 * Import & Export, as the website's: import a Goodreads, StoryGraph, or Carrel export, follow its progress, and sort out
 * the books it couldn't match for sure; or save the library to take elsewhere.
 */
@Composable
fun ImportExportSettings(openBook: (String) -> Unit) {
    val container = LocalContainer.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    var latest by remember { mutableStateOf<ImportStatus?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Bumped to start following again, e.g. after a new import starts.
    var follow by remember { mutableIntStateOf(0) }
    // An import with nothing left to sort out is cleared away, but not while the reader watches: only one that was
    // already sorted out when the page opened is hidden.
    var hiddenId by remember { mutableStateOf<Long?>(null) }

    // Imports run in the background; check on one every few seconds while it's going and this page is showing.
    LaunchedEffect(follow) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val status = try {
                    container.api.getOrNull("/imports/latest", ImportStatus.serializer())
                } catch (e: ApiException) {
                    error = e.message
                    // Once there's something to show, keep it and try again; before that, the error shows instead.
                    if (!loaded) return@repeatOnLifecycle
                    delay(POLL_MS)
                    continue
                }
                error = null
                if (!loaded && status?.settled == true) hiddenId = status.id
                // When an import finishes, everything built from the library (shelves, suggestions, the year) is out of date.
                val previous = latest
                if (previous != null && previous.id == status?.id && previous.state != ImportState.Done && status.state == ImportState.Done) {
                    container.store.markStale()
                    // Including the library this page shows, for the export.
                    library.retry()
                }
                latest = status
                loaded = true
                if (status?.active != true) break
                delay(POLL_MS)
            }
        }
    }

    fun started(status: ImportStatus) {
        latest = status
        follow++
    }

    SettingsPage(SettingsSection.ImportExport) {
        val items = library.loaded.data
        when {
            !loaded && error != null -> ErrorNotice(error.orEmpty(), { follow++ })
            items == null && library.loaded.error != null -> ErrorNotice(library.loaded.error.message.orEmpty(), library.retry)
            !loaded || items == null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(1f)
                SkeletonLine(0.6f)
            }
            else -> {
                val status = latest?.takeIf { it.id != hiddenId }
                Note("Bring your shelves, ratings, and reading dates over from Goodreads or StoryGraph, or take them with you.")
                Gap(12)
                ExportLibrary(items.isNotEmpty())
                Gap(40)
                when {
                    status != null && status.active -> ImportProgress(status)
                    status != null -> AnotherImport(items.isNotEmpty(), ::started)
                    else -> UploadForm(items.isNotEmpty(), heading = true, onStarted = ::started)
                }
                when (status?.state) {
                    ImportState.Failed -> ImportFailed(status, ::started)
                    ImportState.Done -> ImportResults(status, openBook) {
                        // Each book sorted out changes the library and the import's counts.
                        follow++
                        library.retry()
                    }
                    else -> {}
                }
            }
        }
    }
}

private enum class ExportOption(val format: String, val label: String, val file: String, val description: String) {
    Goodreads(
        "goodreads",
        "For Goodreads or StoryGraph",
        "carrel-library-goodreads",
        "In Goodreads’ format, which Goodreads and StoryGraph can both import. It keeps your shelves, ratings (rounded " +
            "down to whole stars), latest finish dates, and how many times you’ve read each book.",
    ),
    Carrel(
        "carrel",
        "Full Carrel export",
        "carrel-library",
        "Everything, including every read’s start and finish dates, half stars, and your progress. Keep it as a backup, " +
            "or import it back into Carrel.",
    ),
}

/** Saves the reader's library as a CSV, in Goodreads' format or Carrel's own, wherever they choose. */
@Composable
private fun ExportLibrary(libraryHasBooks: Boolean) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saving = rememberSaving()
    var preparing by remember { mutableStateOf<ExportOption?>(null) }
    var saved by remember { mutableStateOf<ExportOption?>(null) }
    // Downloaded first, so a failed download leaves no empty file behind; then the reader chooses where it goes.
    var download by remember { mutableStateOf<Pair<ExportOption, ByteArray>?>(null) }
    val place = rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri ->
        val (option, bytes) = download ?: return@rememberLauncherForActivityResult
        download = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException() }
                saved = option
            } catch (e: IOException) {
                saving.error = "Carrel couldn’t save the file there. Try somewhere else."
            }
        }
    }

    SectionTitle("Export your library")
    if (!libraryHasBooks) {
        Text(
            "Nothing to export yet. Books you add to your library can be saved here.",
            style = Carrel.type.body,
            color = Carrel.colors.inkSoft,
            modifier = Modifier.padding(top = 14.dp),
        )
        return
    }
    ExportOption.entries.forEach { option ->
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceName(option.label)
            Text(option.description, style = Carrel.type.body, color = Carrel.colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryButton(if (preparing == option) "Preparing…" else "Save CSV", {
                    saved = null
                    preparing = option
                    saving.run {
                        try {
                            download = option to container.api.download("/library/export?format=${option.format}")
                            place.launch("${option.file}-${LocalDate.now()}.csv")
                        } finally {
                            preparing = null
                        }
                    }
                })
                if (saved == option) Text("Saved.", style = Carrel.type.body, color = Carrel.colors.inkFaint)
            }
        }
    }
    saving.error?.let { FormMessage(it, Tone.Error, Modifier.padding(top = 12.dp)) }
}

/** After an import, the form for the next one stays folded away above the results until it's wanted. */
@Composable
private fun AnotherImport(libraryHasBooks: Boolean, onStarted: (ImportStatus) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SecondaryButton(if (open) "Import another export ▴" else "Import another export ▾", { open = !open })
    if (open) {
        Gap(20)
        UploadForm(libraryHasBooks, heading = false, onStarted = onStarted)
    }
    Gap(32)
}

@Composable
private fun UploadForm(libraryHasBooks: Boolean, heading: Boolean, onStarted: (ImportStatus) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val saving = rememberSaving()
    var file by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) {
            file = uri
            fileName = displayName(context, uri)
            saving.error = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (heading) SectionTitle("Import your library")
        // Recommended: Goodreads exports carry Goodreads ids, which match almost every book exactly.
        Source("Goodreads", "Go to **My Books**, choose **Import and export**, then **Export Library**. Download the file once it’s ready.", recommended = true)
        Source("StoryGraph", "Open **Manage Account**, choose **Export StoryGraph Library**, then download the file once it’s ready.")
        Source("Carrel", "A **Full Carrel export** from above brings back everything, including every read’s dates and your progress.")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Export file (CSV)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryButton(if (file != null) "Choose another file" else "Choose file", { picker.launch(CSV_TYPES) })
                Text(
                    fileName ?: if (file != null) "File chosen" else "No file chosen",
                    style = Carrel.type.body,
                    color = if (file != null) Carrel.colors.ink else Carrel.colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (libraryHasBooks) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Books already in your library")
                Choices(listOf(false to "Skip existing", true to "Overwrite existing"), overwrite, { overwrite = it })
                Text(
                    if (overwrite) "The export replaces the status, rating, and reading dates of books you already have."
                    else "Books you already have stay as they are.",
                    style = Carrel.type.body,
                    color = Carrel.colors.inkFaint,
                )
            }
        }

        saving.error?.let { FormMessage(it, Tone.Error) }
        PrimaryButton(if (saving.busy) "Uploading…" else "Start import", {
            val chosen = file ?: return@PrimaryButton
            saving.run {
                val csv = try {
                    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(chosen)?.use { it.readBytes() } }
                } catch (e: IOException) {
                    null
                } ?: throw ApiException(0, "Carrel couldn’t open that file. Choose it again.")
                val existing = if (overwrite) "overwrite" else "skip"
                onStarted(container.api.upload("/imports?existing=$existing", csv, ContentType.Text.CSV, ImportStatus.serializer()))
            }
        }, enabled = file != null && !saving.busy)
    }
}

/** How to get an export from one of the sites, with its steps in bold (written **like this**). */
@Composable
private fun Source(name: String, steps: String, recommended: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceName(name)
            if (recommended) Text("(Recommended)", style = Carrel.type.mono, color = Carrel.colors.inkSoft)
        }
        Text(bolded(steps), style = Carrel.type.body, color = Carrel.colors.ink)
    }
}

@Composable
private fun SourceName(name: String) = Text(name, style = Carrel.type.heading, color = Carrel.colors.ink)

private fun bolded(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

@Composable
private fun ImportProgress(status: ImportStatus) {
    val done = status.total - status.remaining
    val percent = if (status.total > 0) done * 100.0 / status.total else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Importing from ${status.source.label}")
        ProgressBar(percent)
        Text("${formatCount(done)} of ${formatCount(status.total)} books imported", style = Carrel.type.mono, color = Carrel.colors.ink)
        Text(
            if (status.state == ImportState.Waiting) {
                "Paused until Hardcover’s daily limit resets. It carries on by itself, so there’s nothing you need to do."
            } else {
                "This carries on in the background, so you can leave this page. Bigger libraries take a few minutes."
            },
            style = Carrel.type.body,
            color = Carrel.colors.inkSoft,
        )
    }
    Gap(32)
}

@Composable
private fun ImportFailed(status: ImportStatus, onResumed: (ImportStatus) -> Unit) {
    val container = LocalContainer.current
    val saving = rememberSaving()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Import stopped")
        Text(
            "Your ${status.source.label} import ran into trouble after looking up ${formatCount(status.total - status.remaining)} " +
                "of ${formatCount(status.total)} books. Trying again carries on from there.",
            style = Carrel.type.body,
            color = Carrel.colors.ink,
        )
        PrimaryButton("Try again", { saving.run { onResumed(container.api.post("/imports/${status.id}/resume", ImportStatus.serializer())) } }, enabled = !saving.busy)
        saving.error?.let { FormMessage(it, Tone.Error) }
    }
}

@Composable
private fun ImportResults(status: ImportStatus, openBook: (String) -> Unit, onChanged: () -> Unit) {
    val container = LocalContainer.current
    var review by remember(status.id) { mutableStateOf<List<ImportReviewItem>?>(null) }
    var error by remember(status.id) { mutableStateOf<String?>(null) }
    var version by remember(status.id) { mutableIntStateOf(0) }
    LaunchedEffect(status.id, version) {
        try {
            review = container.api.get("/imports/${status.id}/review", ListSerializer(ImportReviewItem.serializer()))
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
    }
    val changed = {
        version++
        onChanged()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Imported from ${status.source.label}")
        Text("Found ${formatCount(status.matched)} of the ${formatCount(status.total)} books in your export.", style = Carrel.type.body, color = Carrel.colors.ink)
        if (status.settled) Text("Everything’s sorted, so there’s nothing left to check.", style = Carrel.type.body, color = Carrel.colors.inkSoft)
    }
    error?.let { ErrorNotice(it, { version++ }) }
    val items = review.orEmpty()
    // Missing books first: they aren't in the library at all.
    ReviewSection(
        "Couldn’t find",
        "Search for each one to add it, or skip it.",
        items.filter { it.match == ImportMatch.NotFound },
    ) { ReviewRow(status.id, it, openBook, changed) }
    ReviewSection(
        "Check these",
        "These were matched by title and author rather than an ISBN, so they may be a different book. They’re in your library already.",
        items.filter { it.match == ImportMatch.ByTitle },
    ) { ReviewRow(status.id, it, openBook, changed) }
}

@Composable
private fun ReviewSection(title: String, note: String, items: List<ImportReviewItem>, row: @Composable (ImportReviewItem) -> Unit) {
    if (items.isEmpty()) return
    Gap(36)
    SectionTitle("$title (${items.size})")
    Text(note, style = Carrel.type.body, color = Carrel.colors.inkSoft, modifier = Modifier.padding(top = 12.dp))
    items.forEach { row(it) }
}

@Composable
private fun ReviewRow(importId: Long, item: ImportReviewItem, openBook: (String) -> Unit, onChanged: () -> Unit) {
    val container = LocalContainer.current
    val saving = rememberSaving()
    var picking by rememberSaveable(item.id) { mutableStateOf(false) }
    val exported = splitSeries(item.title)
    val path = "/imports/$importId/items/${item.id}"

    fun act(action: String, choice: ChooseBookRequest? = null) = saving.run {
        if (choice == null) {
            container.api.post("$path/$action")
        } else {
            container.api.send(HttpMethod.Post, "$path/$action", choice, ChooseBookRequest.serializer())
        }
        container.store.markStale()
        onChanged()
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(exported.title, style = Carrel.type.body, color = Carrel.colors.ink)
            SeriesLine(exported.series, exported.position)
            if (item.authors.isNotEmpty()) Text(listNames(item.authors.take(3)), style = Carrel.type.body, color = Carrel.colors.inkSoft)
        }
        item.book?.let { book ->
            Match(book.coverUrl, book.title, book.authors, book.seriesName, seriesPosition(book.seriesPosition), book.firstPublishedYear) {
                openBook("/books/${book.id}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (item.match == ImportMatch.ByTitle) LinkButton("Looks right", { act("confirm") }, color = Carrel.colors.ink)
            LinkButton(if (item.match == ImportMatch.ByTitle) "Wrong book" else "Find it", { picking = !picking }, color = Carrel.colors.ink)
            if (item.match == ImportMatch.NotFound) LinkButton("Skip", { act("dismiss") }, color = Carrel.colors.ink)
        }
        if (picking) {
            BookPicker(pickerQuery(item.title, item.authors), saving.busy, openBook) { result ->
                act("choose", ChooseBookRequest(result.hardcoverId, result.openLibraryWorkId))
            }
        }
        saving.error?.let { FormMessage(it, Tone.Error) }
    }
    HorizontalDivider(color = Carrel.colors.rule)
}

/** Searches for the right book, starting from the exported title and author. */
@Composable
private fun BookPicker(initialQuery: String, busy: Boolean, openBook: (String) -> Unit, onPick: (BookSearchResult) -> Unit) {
    val api = LocalContainer.current.api
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<BookSearchResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.isEmpty()) return@LaunchedEffect
        results = null
        error = null
        try {
            results = api.get("/books/search?q=${query.encodeURLParameter()}&page=1", BookSearchResponse.serializer()).results
        } catch (e: ApiException) {
            error = e.message
        }
    }

    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(
            "Search for the book",
            text,
            { text = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onDone = { query = text.trim() },
        )
        when {
            error != null -> FormMessage(error.orEmpty(), Tone.Error)
            results == null && query.isNotEmpty() -> Text("Searching…", style = Carrel.type.body, color = Carrel.colors.inkSoft)
            results?.isEmpty() == true -> Text("No books found. Try different words.", style = Carrel.type.body, color = Carrel.colors.inkSoft)
        }
        results?.take(PICKER_RESULTS)?.forEach { result ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Match(
                    result.coverUrl, result.title, result.authors, result.seriesName, seriesPosition(result.seriesPosition), result.releaseYear,
                    Modifier.weight(1f),
                ) { openBook(result.bookPath) }
                LinkButton("Choose", { if (!busy) onPick(result) }, color = Carrel.colors.ink)
            }
        }
    }
}

/** A book a row might be: its cover, title, series, authors, and year. Opens the book. */
@Composable
private fun Match(
    coverUrl: String?,
    title: String,
    authors: List<String>,
    seriesName: String?,
    position: String?,
    year: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(coverUrl, title, authors.firstOrNull(), 44.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Carrel.type.body, color = Carrel.colors.ink)
            SeriesLine(seriesName, position)
            Text(
                listOfNotNull(listNames(authors.take(3)).ifEmpty { null }, year?.toString()).joinToString(" · "),
                style = Carrel.type.body,
                color = Carrel.colors.inkSoft,
            )
        }
    }
}

/** A book's series and number on its own line under the title, so long titles can't push it out of sight. */
@Composable
private fun SeriesLine(name: String?, position: String?) {
    if (name == null) return
    Text("${position?.let { "Book $it · " }.orEmpty()}$name", style = Carrel.type.mono, color = Carrel.colors.inkSoft)
}

/** The chosen file's name, as the app it came from tells it. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
