package uk.co.zenithal.carrel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.Loaded
import uk.co.zenithal.carrel.data.OwnedBook

/** Something a screen loads from the API, and a way to try again after it fails. */
class Loadable<T>(val loaded: Loaded<T>, val retry: () -> Unit)

/** The saved copy of `path` straight away, then a fresh one (see Store). Nothing while `path` is null. */
@Composable
fun <T> rememberLoaded(path: String?, serializer: KSerializer<T>): Loadable<T> {
    val store = LocalContainer.current.store
    var attempt by remember { mutableIntStateOf(0) }
    val flow: Flow<Loaded<T>> = remember(path, attempt) {
        if (path == null) flowOf(Loaded(null, refreshing = false, error = null)) else store.observe(path, serializer)
    }
    val loaded by flow.collectAsStateWithLifecycle(Loaded<T>(null, refreshing = path != null, error = null))
    return Loadable(loaded) { attempt++ }
}

/**
 * Hardcover ids of the books in the reader's library, so suggestions can leave them out. Null until the library has
 * loaded, so owned books don't appear and then vanish; empty when signed out, or if the library can't be loaded.
 */
@Composable
fun rememberOwnedHardcoverIds(): Set<Long>? {
    val container = LocalContainer.current
    val session by container.session.state.collectAsStateWithLifecycle()
    val signedIn = session is Session.SignedIn
    val library = rememberLoaded(if (signedIn) LIBRARY_PATH else null, ListSerializer(OwnedBook.serializer())).loaded
    return when {
        !signedIn -> emptySet()
        library.data != null -> remember(library.data) { library.data.mapNotNull { it.book.hardcoverId }.toSet() }
        library.error != null -> emptySet()
        else -> null
    }
}
