package uk.co.zenithal.carrel.data

import java.net.URI
import java.net.URLDecoder

/**
 * Where a link, a share, or a launcher shortcut takes the reader. Every part has been checked, since links come from
 * anywhere and parts of them become API paths.
 */
sealed interface Destination {
    /** A book by the API path that loads it, as BookRoute. */
    data class Book(val path: String) : Destination
    data class Series(val hardcoverId: Int, val fromBook: Int?) : Destination
    data object Genres : Destination
    data class Genre(val slug: String) : Destination
    /** The Search tab, searching for `text` when there is some. */
    data class Search(val text: String?) : Destination
    /** The book with this ISBN, or a search for `fallback` when no book has it. */
    data class Isbn(val isbn: String, val fallback: String) : Destination
    data object Library : Destination
    data class Reader(val username: String) : Destination
    data class ReaderShelf(val username: String, val status: ReadingStatus) : Destination
    /** The signed-in reader's shelf at its old address, /shelves/read. */
    data class OwnShelf(val status: ReadingStatus) : Destination
}

private val DIGITS = Regex("""\d{1,10}""")
private val OPEN_LIBRARY_WORK = Regex("""OL\d{1,12}W""")
private val GENRE_SLUG = Regex("""[a-z0-9]+(?:-[a-z0-9]+)*""")

/**
 * Where a link to one of the website's pages goes in the app, or null for any other address. `hosts` are the
 * website's (with a port when it has one, as localhost:5173).
 */
fun linkDestination(url: String, hosts: Set<String>): Destination? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val host = uri.host?.lowercase() ?: return null
    if (host !in hosts && "$host:${uri.port}" !in hosts) return null
    // The raw path, still encoded, so an encoded slash can't sneak a segment past the checks below.
    val parts = uri.rawPath.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
    val query = queryParameters(uri.rawQuery)

    return when {
        parts.size == 2 && parts[0] == "books" && DIGITS.matches(parts[1]) -> Destination.Book("/books/${parts[1]}")
        parts.size == 3 && parts[0] == "books" && parts[1] == "hardcover" && DIGITS.matches(parts[2]) -> Destination.Book("/books/hardcover/${parts[2]}")
        parts.size == 3 && parts[0] == "books" && parts[1] == "openlibrary" && OPEN_LIBRARY_WORK.matches(parts[2]) -> Destination.Book("/books/openlibrary/${parts[2]}")
        parts.size == 3 && parts[0] == "series" && parts[1] == "hardcover" && DIGITS.matches(parts[2]) ->
            Destination.Series(parts[2].toInt(), query["book"]?.takeIf(DIGITS::matches)?.toInt())
        parts == listOf("genres") -> Destination.Genres
        parts.size == 2 && parts[0] == "genres" && GENRE_SLUG.matches(parts[1]) -> Destination.Genre(parts[1])
        parts == listOf("search") -> Destination.Search(query["q"]?.trim()?.take(MAX_SEARCH)?.ifEmpty { null })
        parts.size == 2 && parts[0] == "shelves" -> ReadingStatus.fromSlug(parts[1])?.let { Destination.OwnShelf(it) }
        parts.isNotEmpty() && parts[0].startsWith("@") -> {
            val username = parts[0].drop(1).takeIf(VALID_USERNAME::matches) ?: return null
            when {
                parts.size == 1 -> Destination.Reader(username)
                parts.size == 3 && parts[1] == "shelves" -> ReadingStatus.fromSlug(parts[2])?.let { Destination.ReaderShelf(username, it) }
                else -> null
            }
        }
        else -> null
    }
}

private fun queryParameters(raw: String?): Map<String, String> =
    raw.orEmpty().split('&').mapNotNull { pair ->
        val (key, value) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        decoded(value)?.let { key to it }
    }.toMap()

/** The longest search the Search tab's box takes. */
private const val MAX_SEARCH = 200

private val URL = Regex("""https?://\S+""")
private val ISBN_13 = Regex("""(?<![\d-])97[89](?:[ -]?\d){10}(?![\d-])""")
/** An ISBN-10 named as one in the text ("ISBN 0-575-07996-X"); other ten-digit numbers could be anything. */
private val LABELLED_ISBN_10 = Regex("""ISBN(?:-10)?:?\s*((?:\d[ -]?){9}[\dXx])\b""", RegexOption.IGNORE_CASE)
/** Amazon's product id in a link, which for a printed book is its ISBN-10. */
private val AMAZON_PRODUCT = Regex("""/(?:dp|gp/product)/([0-9A-Za-z]{10})(?:[/?]|$)""")
private val GOODREADS_BOOK = Regex("""/book/show/\d+[-.]([^/?#]+)""")
private val HARDCOVER_BOOK = Regex("""/books/([a-z0-9-]+)""")
private val AMAZON_SLUG = Regex("""^/([^/]+)/(?:dp|gp/product)/""")
/** Site names that page titles end or start with, to leave out of a search. */
private val SITE_NAMES = Regex("""\s*[|–—-]\s*(?:Goodreads|The StoryGraph|StoryGraph|Hardcover|Amazon\.[a-z.]+)\s*$|^\s*Amazon\.[a-z.]+\s*:\s*""", RegexOption.IGNORE_CASE)
/** Anything in brackets, as "(The Kingkiller Chronicle, #1)", which tends to throw a search off. */
private val BRACKETED = Regex("""\s*[(\[][^)\]]*[)\]]""")

/**
 * Where something shared into Carrel from another app goes: a link to one of Carrel's pages opens that page; a book's
 * ISBN (in the text, or in an Amazon link) opens that book; anything else searches for the title the share gives,
 * from the page title or the link itself, else for the text shared. Null when there's nothing to go on.
 */
fun sharedDestination(text: String?, subject: String?, hosts: Set<String>): Destination? {
    val shared = text.orEmpty()
    val urls = URL.findAll(shared).map { it.value.trimEnd('.', ',', ')', '"', '\'') }.toList()
    urls.firstNotNullOfOrNull { linkDestination(it, hosts) }?.let { return it }

    val title = cleanTitle(subject)
        ?: urls.firstNotNullOfOrNull(::titleFromLink)
        ?: cleanTitle(URL.replace(shared, " "))
    val isbn = ISBN_13.findAll(shared).map { it.value }.firstNotNullOfOrNull(::validIsbn)
        ?: LABELLED_ISBN_10.findAll(shared).map { it.groupValues[1] }.firstNotNullOfOrNull(::validIsbn)
        ?: urls.firstNotNullOfOrNull { url -> pathOf(url)?.let { AMAZON_PRODUCT.find(it)?.groupValues?.get(1) }?.let(::validIsbn) }
    return when {
        isbn != null -> Destination.Isbn(isbn, title ?: isbn)
        title != null -> Destination.Search(title)
        else -> null
    }
}

/** A title from a book's address on Goodreads, Hardcover, or Amazon, which put it in the path. */
private fun titleFromLink(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val path = uri.rawPath ?: return null
    val slug = when {
        host == "goodreads.com" -> GOODREADS_BOOK.find(path)?.groupValues?.get(1)
        host == "hardcover.app" -> HARDCOVER_BOOK.find(path)?.groupValues?.get(1)
        host.startsWith("amazon.") -> AMAZON_SLUG.find(path)?.groupValues?.get(1)
        else -> null
    } ?: return null
    return cleanTitle((decoded(slug) ?: slug).replace(Regex("[-_.]+"), " "))
}

/** A percent-encoded part of an address, decoded; null if it's malformed. */
private fun decoded(text: String): String? =
    try {
        // The Charset overload is newer than older phones' Android.
        URLDecoder.decode(text, "UTF-8")
    } catch (_: IllegalArgumentException) {
        null
    }

private fun pathOf(url: String) = runCatching { URI(url).rawPath }.getOrNull()

/** A page title or text made into a search: no site name, nothing in brackets, and "by" left out. */
private fun cleanTitle(text: String?): String? =
    text?.replace(SITE_NAMES, "")
        ?.replace(BRACKETED, "")
        ?.replace(Regex("""\s+by\s+""", RegexOption.IGNORE_CASE), " ")
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.take(MAX_SEARCH)
        ?.ifEmpty { null }

/** The ISBN's digits (and a final X), if its check digit is right; null otherwise. */
fun validIsbn(text: String): String? {
    val isbn = text.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
    return when (isbn.length) {
        10 -> isbn.takeIf {
            it.take(9).all(Char::isDigit) &&
                it.mapIndexed { index, c -> (10 - index) * (if (c == 'X') 10 else c.digitToInt()) }.sum() % 11 == 0
        }
        13 -> isbn.takeIf { it.all(Char::isDigit) && it.mapIndexed { index, c -> c.digitToInt() * if (index % 2 == 0) 1 else 3 }.sum() % 10 == 0 }
        else -> null
    }
}
