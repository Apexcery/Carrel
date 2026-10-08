package uk.co.zenithal.carrel.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.BookDetail
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * Looks up a shared book by its ISBN, then opens it (`onFound`, with its path), or searches for `fallback` when no book
 * has that ISBN (`onNotFound`).
 */
@Composable
fun FindBookScreen(isbn: String, fallback: String, onFound: (path: String) -> Unit, onNotFound: (text: String) -> Unit) {
    val api = LocalContainer.current.api
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt) {
        error = null
        try {
            val book = api.getOrNull("/books/isbn/$isbn", BookDetail.serializer())
            if (book != null) onFound("/books/${book.id}") else onNotFound(fallback)
        } catch (e: ApiException) {
            error = e.message
        }
    }
    Column(Modifier.fillMaxSize().background(Carrel.colors.paper).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Kicker("ISBN $isbn")
        error?.let { ErrorNotice(it, { attempt++ }, Modifier.padding(top = 24.dp)) }
            ?: Text("Finding the book…", style = Carrel.type.displayMedium, color = Carrel.colors.ink, modifier = Modifier.padding(top = 8.dp))
    }
}
