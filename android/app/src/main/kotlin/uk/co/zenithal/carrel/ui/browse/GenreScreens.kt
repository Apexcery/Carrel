package uk.co.zenithal.carrel.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.data.BookSuggestion
import uk.co.zenithal.carrel.data.GENRES_PATH
import uk.co.zenithal.carrel.data.GenreIndex
import uk.co.zenithal.carrel.data.GenreLink
import uk.co.zenithal.carrel.data.GenreShelves
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.GenreLinks
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.ShelfSkeleton
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.components.SuggestionShelf
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Search results shown at most; a few letters can match hundreds of genres. */
private const val MATCHES_SHOWN = 40

/** A genre's page: its most read books, its best rated, and its new releases, as on the website. */
@Composable
fun GenreScreen(slug: String, openBook: (path: String) -> Unit) {
    val genre = rememberLoaded("/genres/$slug", GenreShelves.serializer())
    val data = genre.loaded.data
    val open = { b: BookSuggestion -> openBook("/books/hardcover/${b.hardcoverId}") }
    Column(Modifier.fillMaxSize().background(Carrel.colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Kicker("Genres")
        when {
            data != null -> {
                Text(data.name, style = Carrel.type.displayMedium, color = Carrel.colors.ink, modifier = Modifier.padding(top = 8.dp))
                if (listOf(data.popular, data.topRated, data.newReleases).all { it.isEmpty() }) {
                    Text("Hardcover doesn’t have enough books in this genre to show yet.", style = Carrel.type.body, color = Carrel.colors.inkSoft, modifier = Modifier.padding(top = 28.dp))
                }
                SuggestionShelf("Popular", data.popular, open)
                SuggestionShelf("Top rated", data.topRated, open)
                SuggestionShelf("New releases", data.newReleases, open)
            }
            genre.loaded.error != null -> ErrorNotice(genre.loaded.error.message.orEmpty(), genre.retry)
            else -> {
                SkeletonLine(0.6f, 36.dp, Modifier.padding(top = 8.dp))
                repeat(3) { ShelfSkeleton() }
            }
        }
    }
}

/** Every genre Carrel knows of: the hand-picked ones in two groups, and a search across them and the rest. */
@Composable
fun GenresScreen(openGenre: (GenreLink) -> Unit) {
    val genres = rememberLoaded(GENRES_PATH, GenreIndex.serializer())
    val data = genres.loaded.data
    var query by rememberSaveable { mutableStateOf("") }
    val colors = Carrel.colors
    Column(Modifier.fillMaxSize().background(colors.paper).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Kicker("Browse")
        Text("Genres", style = Carrel.type.displayMedium, color = colors.ink, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        when {
            data != null -> {
                Box(
                    Modifier.fillMaxWidth().border(1.dp, colors.ruleStrong, RoundedCornerShape(2.dp)).background(colors.paperRaised).padding(horizontal = 13.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text("Find a genre", style = Carrel.type.body.copy(fontStyle = FontStyle.Italic), color = colors.inkFaint)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Carrel.type.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val text = query.trim()
                if (text.isNotEmpty()) {
                    val matches = findGenres(data, text)
                    if (matches.isEmpty()) {
                        Text("No genres match “$text”.", style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 24.dp))
                    } else {
                        GenreLinks(matches, openGenre, Modifier.padding(top = 24.dp))
                    }
                } else {
                    Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("Fiction")
                        GenreLinks(data.fiction, openGenre)
                    }
                    Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("Nonfiction")
                        GenreLinks(data.nonfiction, openGenre)
                    }
                }
            }
            genres.loaded.error != null -> ErrorNotice(genres.loaded.error.message.orEmpty(), genres.retry)
            else -> SkeletonLine(1f, 120.dp)
        }
    }
}

/** Genres whose name has the text in it: those starting with it first, then the listed ones, then the most used. */
private fun findGenres(index: GenreIndex, text: String): List<GenreLink> {
    val matching = (index.fiction + index.nonfiction + index.other).filter { it.name.contains(text, ignoreCase = true) }
    val (starting, rest) = matching.partition { it.name.startsWith(text, ignoreCase = true) }
    return (starting + rest).take(MATCHES_SHOWN)
}
