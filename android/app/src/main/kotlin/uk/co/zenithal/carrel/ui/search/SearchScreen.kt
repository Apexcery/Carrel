package uk.co.zenithal.carrel.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.BookSearchResult
import uk.co.zenithal.carrel.data.GENRES_PATH
import uk.co.zenithal.carrel.data.GenreIndex
import uk.co.zenithal.carrel.data.GenreLink
import uk.co.zenithal.carrel.data.displaySubtitle
import uk.co.zenithal.carrel.data.formatCount
import uk.co.zenithal.carrel.data.listNames
import uk.co.zenithal.carrel.data.seriesPosition
import uk.co.zenithal.carrel.ui.components.Cover
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.GenreLinks
import uk.co.zenithal.carrel.ui.components.HardcoverRating
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SkeletonLine
import uk.co.zenithal.carrel.ui.rememberLoaded
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * The Search tab: a search box, then results as the website lists them. Before searching, the reader's recent
 * searches and the genres to browse.
 */
@Composable
fun SearchScreen(
    openBook: (path: String) -> Unit,
    openSeries: (hardcoverId: Int, fromBook: Int?) -> Unit,
    openGenre: (GenreLink) -> Unit,
    openAllGenres: () -> Unit,
) {
    val container = LocalContainer.current
    val model = viewModel { SearchViewModel(container.api, container.recentSearches) }
    val session by container.session.state.collectAsStateWithLifecycle()
    val userId = (session as? Session.SignedIn)?.userId.orEmpty()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by rememberSaveable { mutableStateOf(model.query.orEmpty()) }
    // Read again after each search or clear, so the list stays current.
    var recentVersion by remember { mutableStateOf(0) }
    val recent = remember(userId, recentVersion, model.query) { container.recentSearches.get(userId) }
    val list = rememberLazyListState()

    fun search(q: String) {
        if (q.isBlank()) return
        text = q.trim()
        model.search(q, userId)
        keyboard?.hide()
        focus.clearFocus()
    }

    // Load the next page as the reader nears the end of what's shown.
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, model.results.size) {
        if (nearEnd && model.results.isNotEmpty() && model.error == null) model.loadMore()
    }

    LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(Carrel.colors.paper), contentPadding = PaddingValues(16.dp, 20.dp)) {
        item {
            SearchBox(
                text = text,
                onTextChange = {
                    text = it
                    if (it.isBlank()) model.clear()
                },
                onSearch = { search(text) },
            )
        }
        val query = model.query
        if (query == null) {
            item { Idle(recent, ::search, { container.recentSearches.clear(userId); recentVersion++ }, openGenre, openAllGenres) }
        } else {
            item {
                Column(Modifier.padding(top = 28.dp, bottom = 12.dp)) {
                    Kicker("Search")
                    Text("“$query”", style = Carrel.type.displayMedium, color = Carrel.colors.ink, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    model.found?.let {
                        Text(
                            if (it == 0) "No books found" else "${formatCount(it)} ${if (it == 1) "book" else "books"}",
                            style = Carrel.type.mono,
                            color = Carrel.colors.inkSoft,
                        )
                    }
                }
            }
            itemsIndexed(model.results, key = { _, r -> r.hardcoverId?.toString() ?: r.openLibraryWorkId.orEmpty() }) { index, result ->
                if (index > 0) HorizontalDivider(color = Carrel.colors.rule)
                ResultCard(result, openBook, openSeries)
            }
            if (model.loading) {
                items(if (model.results.isEmpty()) 4 else 1) { ResultSkeleton() }
            }
            model.error?.let { message ->
                item { ErrorNotice(message, { model.loadMore() }) }
            }
        }
    }
}

/** The website's header search box: the field and an accent Search button, joined. */
@Composable
private fun SearchBox(text: String, onTextChange: (String) -> Unit, onSearch: () -> Unit) {
    val colors = Carrel.colors
    Row(Modifier.fillMaxWidth().height(46.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .border(1.dp, colors.ruleStrong, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                .background(colors.paperRaised)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text("Title, author, or ISBN", style = Carrel.type.body.copy(fontStyle = FontStyle.Italic), color = colors.inkFaint)
            }
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 200) onTextChange(it) },
                singleLine = true,
                textStyle = Carrel.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .background(colors.accent, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("SEARCH", style = Carrel.type.monoMedium.copy(letterSpacing = 0.08.em), color = colors.onAccent)
        }
    }
}

/** Before a search: recent searches to repeat, and genres to browse. */
@Composable
private fun Idle(
    recent: List<String>,
    search: (String) -> Unit,
    clearRecent: () -> Unit,
    openGenre: (GenreLink) -> Unit,
    openAllGenres: () -> Unit,
) {
    val genres = rememberLoaded(GENRES_PATH, GenreIndex.serializer()).loaded.data
    Column {
        if (recent.isNotEmpty()) {
            Gap(32)
            SectionTitle("Recent searches")
            Column(Modifier.padding(top = 6.dp)) {
                recent.forEach { q ->
                    Text(
                        q,
                        style = Carrel.type.body,
                        color = Carrel.colors.ink,
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { search(q) }.padding(vertical = 8.dp),
                    )
                }
                LinkButton("Clear recent searches", clearRecent)
            }
        }
        if (genres != null) {
            Gap(32)
            SectionTitle("Fiction")
            GenreLinks(genres.fiction, openGenre, Modifier.padding(top = 14.dp))
            Gap(32)
            SectionTitle("Nonfiction")
            GenreLinks(genres.nonfiction, openGenre, Modifier.padding(top = 14.dp))
            Gap(16)
            LinkButton("Find another genre →", openAllGenres, color = Carrel.colors.accent)
        }
    }
}

@Composable
private fun ResultCard(result: BookSearchResult, openBook: (String) -> Unit, openSeries: (Int, Int?) -> Unit) {
    val colors = Carrel.colors
    val subtitle = displaySubtitle(result.title, result.subtitle)
    val position = seriesPosition(result.seriesPosition)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { openBook(result.bookPath) }.padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Cover(result.coverUrl, result.title, result.authors.firstOrNull(), 64.dp)
        Column(Modifier.weight(1f)) {
            result.seriesName?.let { name ->
                val seriesId = result.seriesHardcoverId
                Text(
                    (position?.let { "Book $it · " } ?: "") + name,
                    style = Carrel.type.mono,
                    color = colors.accent,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .then(if (seriesId != null) Modifier.clickable(role = Role.Button) { openSeries(seriesId, result.hardcoverId) } else Modifier),
                )
            }
            Text(result.title, style = Carrel.type.heading, color = colors.ink)
            subtitle?.let { Text(it, style = Carrel.type.body.copy(fontStyle = FontStyle.Italic), color = colors.inkSoft) }
            val byline = buildList {
                if (result.authors.isNotEmpty()) add("by ${listNames(result.authors.take(3))}")
                result.releaseYear?.let { add(it.toString()) }
            }.joinToString(" · ")
            if (byline.isNotEmpty()) Text(byline, style = Carrel.type.body, color = colors.inkSoft, modifier = Modifier.padding(top = 4.dp))
            HardcoverRating(result.hardcoverRating, result.hardcoverRatingsCount, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ResultSkeleton() {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(64.dp).height(96.dp).background(Carrel.colors.rule))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonLine(0.8f)
            SkeletonLine(0.5f)
        }
    }
}
