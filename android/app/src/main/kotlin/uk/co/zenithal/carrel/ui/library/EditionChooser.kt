package uk.co.zenithal.carrel.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.data.Edition
import uk.co.zenithal.carrel.data.formatDuration
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.theme.Carrel

// Ported from web/src/components/EditionPicker.tsx; keep the two in step.

private val GROUPS = listOf("print" to "Print", "ebook" to "Ebook", "audio" to "Audiobook", null to "Other")

/**
 * The reader's edition, chosen from the book's editions grouped by format, with a search that matches publisher, year,
 * length, language, and ISBN. Shown in place of the edit sheet's form, which "Back" returns to.
 */
@Composable
fun EditionChooser(editions: List<Edition>, selected: Long?, onChoose: (Long?) -> Unit, onBack: () -> Unit) {
    val colors = Carrel.colors
    var query by rememberSaveable { mutableStateOf("") }
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val matches = editions.filter { edition -> searchText(edition).let { text -> terms.all { it in text } } }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 16.dp)) {
        LinkButton("← Back", onBack, color = colors.ink)
        Text("Your edition", style = Carrel.type.heading, color = colors.ink, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Field("Search editions", query, { query = it })
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (query.isBlank()) {
                item { Option("Not specified", null, selected == null) { onChoose(null) } }
            }
            GROUPS.forEach { (format, label) ->
                val group = matches.filter { it.format == format }
                if (group.isNotEmpty()) {
                    item(key = label) {
                        Text(
                            label.uppercase(),
                            style = Carrel.type.monoMedium.copy(letterSpacing = 0.12.em),
                            color = colors.accent,
                            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(group, key = { it.id }) { edition ->
                        Option(
                            listOfNotNull(edition.publisher ?: "Unknown publisher", year(edition)).joinToString(" · "),
                            details(edition).ifEmpty { null },
                            edition.id == selected,
                        ) { onChoose(edition.id) }
                    }
                }
            }
            if (matches.isEmpty()) {
                item { Text("No editions match “$query”.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
    }
}

/** One edition to choose; the chosen one is marked with an accent bar. */
@Composable
private fun Option(main: String, detail: String?, chosen: Boolean, onClick: () -> Unit) {
    val colors = Carrel.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .then(if (chosen) Modifier.background(colors.paperRaised) else Modifier)
            .drawBehind {
                if (chosen) drawLine(colors.accent, Offset(1.5.dp.toPx(), 0f), Offset(1.5.dp.toPx(), size.height), 3.dp.toPx())
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(main, style = Carrel.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = Carrel.type.mono, color = colors.inkSoft) }
    }
}

/** "Print · Tor · 2019 · 340 pp.", as much of it as is known. */
fun editionLabel(edition: Edition): String {
    val format = GROUPS.firstOrNull { it.first == edition.format }?.second ?: "Edition"
    return listOfNotNull(format, edition.publisher, year(edition), length(edition)).joinToString(" · ")
}

private fun year(edition: Edition) = edition.releaseDate?.take(4)

private fun length(edition: Edition) =
    edition.pageCount?.let { "$it pp." } ?: edition.audioSeconds?.let(::formatDuration)

private fun details(edition: Edition) =
    listOfNotNull(length(edition), edition.language?.uppercase(), edition.isbn13 ?: edition.isbn10).joinToString(" · ")

private fun searchText(edition: Edition) =
    listOfNotNull(editionLabel(edition), edition.language, edition.isbn13, edition.isbn10).joinToString(" ").lowercase()
