package uk.co.zenithal.carrel.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.data.CustomShelvesSerializer
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.Loaded
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.MAX_SHELF_NAME
import uk.co.zenithal.carrel.data.SHELVES_PATH
import uk.co.zenithal.carrel.data.contains
import uk.co.zenithal.carrel.data.onCustomShelf
import uk.co.zenithal.carrel.ui.Loadable
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * A book's place on the reader's custom shelves, as the website's picker, whether or not it's in their library: each
 * shelf is a switch that puts the book on it or takes it off, straight away. "New shelf" starts one (private) with the
 * book on it.
 * Without a signal, switching is kept on the phone to send later (see ShelfChanges); a new shelf needs one. Nothing
 * shows until the shelves have loaded.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomShelfPicker(book: BookDetail) {
    val container = LocalContainer.current
    val shelves = rememberLoaded(SHELVES_PATH, CustomShelvesSerializer).loaded.data ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var naming by rememberSaveable { mutableStateOf(false) }
    fun change(action: suspend () -> Unit, onDone: () -> Unit = {}) = scope.change({ busy = it }, { error = it }, action, onDone)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Your shelves")
        if (shelves.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shelves.forEach { shelf ->
                    val on = book.id in shelf
                    ShelfChip(shelf.name, on, enabled = !busy) { change({ container.shelves.setOn(shelf, book, !on) }) }
                }
            }
        }
        LinkButton("New shelf", {
            error = null
            naming = true
        })
        if (!naming) error?.let { FormMessage(it, Tone.Error) }
    }

    if (naming) {
        NameDialog("", "Add", busy, error, { name -> change({ container.shelves.create(name.trim(), book.id) }) { naming = false } }) {
            error = null
            naming = false
        }
    }
}

/**
 * Every book on one of the reader's own custom shelves, as on a status shelf, with whether it's public and ways to
 * switch that, rename it, or delete it (then `onDeleted`).
 */
@Composable
fun CustomShelfScreen(id: Long, openBook: (path: String) -> Unit, onDeleted: () -> Unit) {
    val container = LocalContainer.current
    val colors = Carrel.colors
    val shelves = rememberLoaded(SHELVES_PATH, CustomShelvesSerializer)
    val library = rememberLoaded(LIBRARY_PATH, LibrarySerializer)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    fun change(action: suspend () -> Unit, onDone: () -> Unit = {}) = scope.change({ busy = it }, { error = it }, action, onDone)

    val shelf = shelves.loaded.data?.firstOrNull { it.id == id }
    // Deleted, perhaps on another device.
    if (shelves.loaded.data != null && shelf == null) {
        Column(Modifier.fillMaxSize().background(colors.paper).padding(horizontal = 16.dp, vertical = 24.dp)) {
            Kicker("Not on the shelf")
            Text("This shelf doesn’t exist.", style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    // The library waits for the shelf, so the page shows when both are there.
    val books = Loadable(
        Loaded(
            library.loaded.data?.takeIf { shelf != null },
            library.loaded.refreshing || shelves.loaded.refreshing,
            library.loaded.error ?: shelves.loaded.error?.takeIf { shelf == null },
        ),
    ) {
        library.retry()
        shelves.retry()
    }

    ShelfScreen(
        shelf?.name.orEmpty(),
        null,
        "Your shelves",
        books,
        openBook,
        books = { items -> shelf?.let { onCustomShelf(items, it) }.orEmpty() },
        empty = "Nothing on this shelf yet. Put books on it from their pages.",
    ) {
        if (shelf != null) {
            Column(Modifier.padding(top = 14.dp)) {
                Text(
                    if (shelf.isPublic) "Public: shows on your profile." else "Private: only you can see it.",
                    style = Carrel.type.body,
                    color = colors.inkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LinkButton(if (shelf.isPublic) "Make private" else "Make public", {
                        if (!busy) change({ container.shelves.save(shelf, isPublic = !shelf.isPublic) })
                    }, color = colors.ink)
                    LinkButton("Rename", {
                        error = null
                        renaming = true
                    }, color = colors.ink)
                    LinkButton("Delete shelf", { confirmingDelete = true }, color = colors.danger)
                }
                if (!renaming) error?.let { FormMessage(it, Tone.Error) }
            }
        }
    }

    if (renaming && shelf != null) {
        NameDialog(shelf.name, "Save", busy, error, { name -> change({ container.shelves.save(shelf, name = name.trim()) }) { renaming = false } }) {
            error = null
            renaming = false
        }
    }

    if (confirmingDelete && shelf != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = colors.paperRaised,
            text = { Text("Delete “${shelf.name}”? Its books stay in your library and on your other shelves.", style = Carrel.type.body, color = colors.ink) },
            confirmButton = {
                LinkButton("Delete", {
                    confirmingDelete = false
                    change({ container.shelves.delete(shelf.id) }, onDeleted)
                }, Modifier.padding(horizontal = 8.dp), color = colors.danger)
            },
            dismissButton = { LinkButton("Cancel", { confirmingDelete = false }, Modifier.padding(horizontal = 8.dp)) },
        )
    }
}

/** Runs a change to the shelves, busy meanwhile, keeping the API's message if it fails. */
private fun CoroutineScope.change(
    setBusy: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    action: suspend () -> Unit,
    onDone: () -> Unit,
) = launch {
    setBusy(true)
    setError(null)
    try {
        action()
        onDone()
    } catch (e: ApiException) {
        setError(e.message)
    } finally {
        setBusy(false)
    }
}

/** A shelf's name to choose, for a new shelf or renaming one. */
@Composable
private fun NameDialog(initial: String, confirm: String, busy: Boolean, error: String?, onSave: (String) -> Unit, onClose: () -> Unit) {
    val colors = Carrel.colors
    var name by rememberSaveable { mutableStateOf(initial) }
    val save = { if (!busy) onSave(name) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.paperRaised,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    "Shelf name",
                    name,
                    { if (it.length <= MAX_SHELF_NAME) name = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    onDone = save,
                )
                error?.let { FormMessage(it, Tone.Error) }
            }
        },
        confirmButton = { LinkButton(if (busy) "Saving…" else confirm, save, Modifier.padding(horizontal = 8.dp), color = colors.ink) },
        dismissButton = { LinkButton("Cancel", onClose, Modifier.padding(horizontal = 8.dp)) },
    )
}

/** A shelf as a switch, in the style of Choices: bordered mono, filled with the accent while the book is on it. */
@Composable
private fun ShelfChip(label: String, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = Carrel.colors
    val shape = RoundedCornerShape(2.dp)
    Text(
        label,
        style = Carrel.type.mono.copy(letterSpacing = 0.04.em),
        color = if (on) colors.onAccent else colors.inkSoft,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.6f)
            .border(1.dp, if (on) colors.accent else colors.ruleStrong, shape)
            .background(if (on) colors.accent else Color.Transparent, shape)
            .toggleable(value = on, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
