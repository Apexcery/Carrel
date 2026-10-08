package uk.co.zenithal.carrel.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibraryEntry
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.ProgressUnit
import uk.co.zenithal.carrel.data.ReadInput
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.SaveEntryRequest
import uk.co.zenithal.carrel.data.formatDate
import uk.co.zenithal.carrel.data.formatProgressValue
import uk.co.zenithal.carrel.data.parseProgressValue
import uk.co.zenithal.carrel.data.progressSummary
import uk.co.zenithal.carrel.data.toRequest
import uk.co.zenithal.carrel.ui.components.CheckRow
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private val STATUSES = ReadingStatus.entries.map { it to it.label }
private val UNITS = listOf(ProgressUnit.Page to "pages", ProgressUnit.Percent to "%", ProgressUnit.Seconds to "time (h:mm)")

/**
 * The signed-in reader's shelf entry for a book, as the website's panel. Status and rating save as soon as they change;
 * progress, edition, and reading history are edited together in a sheet behind "Edit" and saved with one button.
 * The entry comes from the saved library, so it shows with no signal; changes need one. Marking the book as read
 * opens the finished sheet, which can open the next in the series (`openBook`).
 */
@Composable
fun ShelfPanel(book: BookDetail, openBook: (path: String) -> Unit) {
    val container = LocalContainer.current
    val haptics = LocalHapticFeedback.current
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }

    val items = library.loaded.data
    if (items == null) {
        library.loaded.error?.let { ErrorNotice(it.message.orEmpty(), library.retry) } ?: PanelFrame {}
        return
    }
    val entry = items.firstOrNull { it.book.id == book.id }?.entry

    // Each change starts from the entry the API last returned, since a save replaces the whole entry.
    fun change(action: suspend () -> Unit, onDone: () -> Unit = {}) = scope.launch {
        busy = true
        error = null
        try {
            action()
            onDone()
        } catch (e: ApiException) {
            error = e.message
        } finally {
            busy = false
        }
    }
    val save = { request: SaveEntryRequest, onDone: () -> Unit -> change({ container.library.save(book, request) }, onDone) }

    PanelFrame {
        Kicker(if (entry != null) "On your shelf" else "Add to your library")
        Choices(STATUSES, entry?.status, { status ->
            if (status != entry?.status) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                save(entry.toRequest().copy(status = status)) {
                    if (status == ReadingStatus.Read) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        finished = true
                    }
                }
            }
        }, enabled = !busy)
        if (entry != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("Your rating")
                StarRating(entry.rating, { rating -> save(entry.toRequest().copy(rating = rating)) {} }, enabled = !busy)
            }
            if (entry.status.inProgress) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(entry.progressPercent)
                    Text(progressSummary(entry), style = Carrel.type.mono, color = Carrel.colors.inkSoft)
                }
            }
            LinkButton("Edit", {
                error = null
                editing = true
            })
        }
        if (!editing) error?.let { FormMessage(it, Tone.Error) }
    }

    if (finished) {
        FinishedSheet(book, { path ->
            finished = false
            openBook(path)
        }) { finished = false }
    }

    if (editing && entry != null) {
        EditSheet(
            book = book,
            entry = entry,
            busy = busy,
            error = error,
            onSave = { request -> save(request) { editing = false } },
            onRemove = { change({ container.library.remove(book.id) }) { editing = false } },
            onClose = {
                error = null
                editing = false
            },
        )
    }
}

/** The website's .shelf-panel: a raised card under an accent rule. */
@Composable
private fun PanelFrame(content: @Composable () -> Unit) {
    val colors = Carrel.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .shadow(3.dp, RoundedCornerShape(1.dp, 1.dp, 3.dp, 3.dp))
            .background(colors.paperRaised)
            .drawBehind { drawLine(colors.accent, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) { content() }
}

/** A read being edited; `key` tells rows apart, since new reads have no id yet. */
private data class ReadDraft(
    val key: String,
    val id: Long?,
    val startedOn: String?,
    val finishedOn: String?,
    val finishedDateUnknown: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    book: BookDetail,
    entry: LibraryEntry,
    busy: Boolean,
    error: String?,
    onSave: (SaveEntryRequest) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Carrel.colors
    var unit by rememberSaveable { mutableStateOf(entry.progressUnit ?: ProgressUnit.Page) }
    var progressText by rememberSaveable { mutableStateOf(entry.progressValue?.let { formatProgressValue(entry.progressUnit ?: ProgressUnit.Page, it) } ?: "") }
    var editionId by rememberSaveable { mutableStateOf(entry.editionId) }
    val reads = remember {
        mutableStateListOf(*entry.reads.map { ReadDraft(it.id.toString(), it.id, it.startedOn, it.finishedOn, it.finishedDateUnknown) }.toTypedArray())
    }
    var problem by remember { mutableStateOf<String?>(null) }
    var choosingEdition by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    val reading = entry.status.inProgress

    fun submit() {
        val progressValue = if (progressText.isBlank()) null else parseProgressValue(unit, progressText)
        if (reading && progressText.isNotBlank() && progressValue == null) {
            problem = "Enter progress as a number${if (unit == ProgressUnit.Seconds) " or hours:minutes, like 3:45" else ""}."
            return
        }
        if (reads.any { it.startedOn != null && it.finishedOn != null && it.startedOn > it.finishedOn }) {
            problem = "A read can’t finish before it starts."
            return
        }
        problem = null
        val request = entry.toRequest().copy(
            editionId = editionId,
            reads = reads.map { ReadInput(it.id, it.startedOn, it.finishedOn, it.finishedDateUnknown && it.finishedOn == null) },
        )
        onSave(if (reading) request.copy(progressUnit = progressValue?.let { unit }, progressValue = progressValue) else request)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
    ) {
        if (choosingEdition) {
            EditionChooser(book.editions, editionId, { id ->
                editionId = id
                choosingEdition = false
            }) { choosingEdition = false }
            return@ModalBottomSheet
        }
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Kicker(entry.status.label)
                Text(book.title, style = Carrel.type.heading, color = colors.ink, modifier = Modifier.padding(top = 6.dp))
            }

            if (reading) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(
                        "Progress",
                        progressText,
                        { progressText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = if (unit == ProgressUnit.Seconds) KeyboardType.Text else KeyboardType.Decimal),
                    )
                    Choices(UNITS, unit, { unit = it })
                }
            }

            if (book.editions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Your edition")
                    val edition = book.editions.firstOrNull { it.id == editionId }
                    Text(
                        edition?.let(::editionLabel) ?: "Not specified",
                        style = Carrel.type.body,
                        color = if (edition != null) colors.ink else colors.inkFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Choose your edition", role = Role.Button) { choosingEdition = true }
                            .underRule(colors.ruleStrong)
                            .padding(vertical = 6.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FieldLabel("Reading history")
                if (reads.isEmpty()) Text("No read dates yet.", style = Carrel.type.body, color = colors.inkSoft)
                reads.forEachIndexed { index, read ->
                    ReadRow(read, { reads[index] = it }) { reads.removeAt(index) }
                }
                LinkButton("Add another read", { reads.add(ReadDraft(UUID.randomUUID().toString(), null, null, null, false)) })
            }

            (problem ?: error)?.let { FormMessage(it, Tone.Error) }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(if (busy) "Saving…" else "Save", ::submit, enabled = !busy)
                LinkButton("Cancel", onClose)
            }
            LinkButton("Remove from library", { confirmingRemove = true }, color = colors.danger)
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            containerColor = colors.paperRaised,
            text = {
                Text(
                    "Remove this book from your library? Its rating, progress, and read dates will be deleted.",
                    style = Carrel.type.body,
                    color = colors.ink,
                )
            },
            confirmButton = {
                LinkButton("Remove", {
                    confirmingRemove = false
                    onRemove()
                }, Modifier.padding(horizontal = 8.dp), color = colors.danger)
            },
            dismissButton = { LinkButton("Cancel", { confirmingRemove = false }, Modifier.padding(horizontal = 8.dp)) },
        )
    }
}

/** One read: when it started and finished, with "date unknown" for a finish whose date was never recorded. */
@Composable
private fun ReadRow(read: ReadDraft, onChange: (ReadDraft) -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().underRule(Carrel.colors.rule).padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DateField("Started", read.startedOn, Modifier.weight(1f)) { onChange(read.copy(startedOn = it)) }
            DateField("Finished", read.finishedOn, Modifier.weight(1f)) { onChange(read.copy(finishedOn = it)) }
        }
        if (read.finishedOn == null) {
            CheckRow("Finished, date unknown", read.finishedDateUnknown, { onChange(read.copy(finishedDateUnknown = it)) })
        }
        LinkButton("Remove this read", onRemove)
    }
}

/** A date shown as "3 Oct 2026", chosen from a calendar, which can also clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: String?, modifier: Modifier, onChange: (String?) -> Unit) {
    val colors = Carrel.colors
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(label)
        Text(
            formatDate(date) ?: "Not set",
            style = Carrel.type.body,
            color = if (date != null) colors.ink else colors.inkFaint,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Choose the date", role = Role.Button) { picking = true }
                .underRule(colors.ruleStrong)
                .padding(vertical = 6.dp),
        )
    }
    if (picking) {
        // The calendar works in UTC midnights, so dates convert at UTC rather than the phone's time zone.
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                LinkButton("OK", {
                    picking = false
                    onChange(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() })
                }, Modifier.padding(horizontal = 12.dp), color = colors.ink)
            },
            dismissButton = {
                Row {
                    LinkButton("Clear", {
                        picking = false
                        onChange(null)
                    }, Modifier.padding(horizontal = 12.dp))
                    LinkButton("Cancel", { picking = false }, Modifier.padding(horizontal = 12.dp))
                }
            },
        ) { DatePicker(state) }
    }
}

/** A rule along the bottom edge, as under the website's fields. */
internal fun Modifier.underRule(color: androidx.compose.ui.graphics.Color) = drawBehind {
    val width = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - width / 2), Offset(size.width, size.height - width / 2), width)
}
