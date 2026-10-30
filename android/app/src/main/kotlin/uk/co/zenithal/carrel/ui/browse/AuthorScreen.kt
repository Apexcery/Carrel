package uk.co.zenithal.carrel.ui.browse

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import uk.co.zenithal.carrel.data.AuthorDetail
import uk.co.zenithal.carrel.data.BookSuggestion
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.components.SuggestionShelf
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel
import uk.co.zenithal.carrel.ui.you.Avatar

/** Characters of the bio's first paragraph shown before "Show more". */
private const val BIO_PREVIEW = 280

/** An author, as on the website: their details and bio, then their books by series, then the rest. */
@Composable
fun AuthorScreen(hardcoverId: Int, openBook: (path: String) -> Unit, openSeries: (hardcoverId: Int) -> Unit) {
    val author = rememberLoaded("/authors/hardcover/$hardcoverId", AuthorDetail.serializer())
    val data = author.loaded.data
    Column(Modifier.fillMaxSize().background(Carrel.colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        when {
            data != null -> AuthorView(data, openBook, openSeries)
            author.loaded.error != null -> ErrorNotice(author.loaded.error.message.orEmpty(), author.retry)
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonLine(0.3f)
                SkeletonLine(0.7f, 36.dp)
                repeat(4) { SkeletonLine(1f) }
            }
        }
    }
}

@Composable
private fun AuthorView(author: AuthorDetail, openBook: (String) -> Unit, openSeries: (Int) -> Unit) {
    val colors = Carrel.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(author.photoUrl, "Photo of ${author.name}")
        Column(Modifier.weight(1f)) {
            Kicker("Author")
            Text(author.name, style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            val meta = listOfNotNull(lifeYears(author.bornYear, author.deathYear), author.location).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = Carrel.type.mono, color = colors.inkSoft)
        }
    }

    author.bio?.let { Bio(it) }

    if (author.series.isEmpty() && author.standalones.isEmpty()) {
        Text("Hardcover doesn’t list any books by ${author.name} yet.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 28.dp))
    }
    val open = { b: BookSuggestion -> openBook("/books/hardcover/${b.hardcoverId}") }
    author.series.forEach { s -> SuggestionShelf(s.name, s.books, open, onTitle = { openSeries(s.hardcoverId) }) }
    SuggestionShelf(if (author.series.isNotEmpty()) "Other books" else "Books", author.standalones, open)
}

/** "1903–1950", "Born 1975", or "Died 1950". */
private fun lifeYears(born: Int?, died: Int?): String? = when {
    born != null && died != null -> "$born–$died"
    born != null -> "Born $born"
    died != null -> "Died $died"
    else -> null
}

/** The bio's opening, up to BIO_PREVIEW characters of its first paragraph (cut at a word), then the rest on request. */
@Composable
private fun Bio(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val first = paragraphs.firstOrNull().orEmpty()
    val preview = if (first.length > BIO_PREVIEW) first.take(first.lastIndexOf(' ', BIO_PREVIEW)) + "…" else first
    val collapsible = paragraphs.size > 1 || preview != first
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        (if (expanded || !collapsible) paragraphs else listOf(preview)).forEach {
            Text(it, style = Carrel.type.body.copy(lineHeight = 1.65.em), color = Carrel.colors.ink)
        }
        if (collapsible) LinkButton(if (expanded) "Show less" else "Show more", { expanded = !expanded })
        Text("Biography from Hardcover", style = Carrel.type.mono, color = Carrel.colors.inkFaint)
    }
}
