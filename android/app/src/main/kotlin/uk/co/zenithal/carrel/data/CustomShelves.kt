package uk.co.zenithal.carrel.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

// Shapes returned by the custom shelf endpoints (see api/Carrel.Api/Library/CustomShelfEndpoints.cs and web/src/types.ts).

/**
 * A shelf the reader named themselves; a book can be on any number of them, in the library or not. Only public ones
 * show on the profile.
 */
@Serializable
data class CustomShelf(
    val id: Long,
    val name: String,
    val isPublic: Boolean,
    /** Most recently shelved first. */
    val books: List<ShelvedBook>,
)

/** A book on a custom shelf, and when it was put there. */
@Serializable
data class ShelvedBook(val book: LibraryBook, val addedAt: String)

/** Leaving out isPublic keeps the current setting (private, for a new shelf). */
@Serializable
data class SaveShelfRequest(val name: String, val isPublic: Boolean? = null)

/** The signed-in reader's custom shelves, A–Z. */
const val SHELVES_PATH = "/shelves"

val CustomShelvesSerializer = ListSerializer(CustomShelf.serializer())

/** Up to 50 characters, as the API checks. */
const val MAX_SHELF_NAME = 50

/** Whether the book is on the shelf. */
operator fun CustomShelf.contains(bookId: Long) = books.any { it.book.id == bookId }

/** The shelf with the book put on it (first, as just shelved) or taken off, as the API would return it. */
fun CustomShelf.withBook(book: LibraryBook, on: Boolean, now: String): CustomShelf = when {
    on && book.id !in this -> copy(books = listOf(ShelvedBook(book, now)) + books)
    !on -> copy(books = books.filter { it.book.id != book.id })
    else -> this
}

/**
 * Changes the reader's custom shelves, then puts the result into the saved list, so every screen showing them changes
 * straight away. Without a signal, putting a book on a shelf or taking it off is kept on the phone (see Outbox) and
 * shows as if saved, as library changes do; so is any such change to a book with one still waiting, so they reach the
 * API in order. Making, renaming, and deleting shelves need a signal.
 */
class ShelfChanges(private val api: ApiClient, private val store: Store, private val outbox: Outbox) {

    /**
     * Starts a shelf (private) with the book on it. The shelf is saved as soon as it's made, so if putting the book on
     * it fails, it still shows (and isn't made again).
     */
    suspend fun create(name: String, bookId: Long) {
        val shelf = api.send(HttpMethod.Post, SHELVES_PATH, SaveShelfRequest(name), SaveShelfRequest.serializer(), CustomShelf.serializer())
        store.update(SHELVES_PATH, CustomShelvesSerializer) { shelves -> inOrder(shelves + shelf) }
        stored(api.put("$SHELVES_PATH/${shelf.id}/books/$bookId", CustomShelf.serializer()))
    }

    /** Renames the shelf, or makes it public or private. */
    suspend fun save(shelf: CustomShelf, name: String = shelf.name, isPublic: Boolean = shelf.isPublic) {
        val saved = api.send(HttpMethod.Put, "$SHELVES_PATH/${shelf.id}", SaveShelfRequest(name, isPublic), SaveShelfRequest.serializer(), CustomShelf.serializer())
        stored(saved)
    }

    /** The books stay in the library. */
    suspend fun delete(id: Long) {
        api.delete("$SHELVES_PATH/$id")
        store.update(SHELVES_PATH, CustomShelvesSerializer) { shelves -> shelves.filter { it.id != id } }
    }

    /** Puts the book on the shelf, or takes it off. */
    suspend fun setOn(shelf: CustomShelf, book: BookDetail, on: Boolean) {
        if (!outbox.isShelvingWaiting(book.id)) {
            val path = "$SHELVES_PATH/${shelf.id}/books/${book.id}"
            try {
                stored(if (on) api.put(path, CustomShelf.serializer()) else api.delete(path, CustomShelf.serializer()))
                return
            } catch (e: ApiException) {
                if (e.status != 0) throw e
            }
        }
        val now = Instant.now().toString()
        store.update(SHELVES_PATH, CustomShelvesSerializer) { shelves ->
            shelves.map { if (it.id == shelf.id) it.withBook(book.toLibraryBook(null), on, now) else it }
        }
        outbox.addShelving(shelf.id, book.id, on)
    }

    private suspend fun stored(shelf: CustomShelf) =
        store.update(SHELVES_PATH, CustomShelvesSerializer) { shelves -> inOrder(shelves.map { if (it.id == shelf.id) shelf else it }) }

    /** A–Z, as the API lists them. */
    private fun inOrder(shelves: List<CustomShelf>) = shelves.sortedWith { a, b -> compareText(a.name, b.name).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
}
