package uk.co.zenithal.carrel.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/**
 * An EPUB added to this phone: Carrel's own copy of it, the Carrel book it's linked to, and where the reader is in it.
 * It belongs to the phone, not an account, so it's there signed in or out; reading it tracks progress in the library
 * of whoever is signed in.
 */
@Entity(tableName = "books")
data class PhoneBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The copy's name in files/books, and its cover's in files/covers (null without one). */
    val file: String,
    val cover: String?,
    /** The file's SHA-256, so adding the same file again finds the copy already here. */
    val fingerprint: String,
    val title: String,
    /** One per line. */
    val authors: String,
    /** The series the file says it's in (Calibre's, or EPUB 3's), and its number in it. */
    val series: String?,
    val seriesPosition: Double?,
    /** ISBNs in the file's own details, one per line, to find its Carrel book. */
    val isbns: String,
    /** The Carrel book whose library entry reading this updates, and its cover, shown in place of the file's. */
    val bookId: Long?,
    val bookCover: String?,
    /** Where the reader is, as a Readium Locator (JSON), and how far through the book, from 0 to 1. */
    val locator: String?,
    val progression: Double?,
    val addedAt: Long,
    val openedAt: Long?,
    /**
     * For a book downloaded from an online library: the library, the book's id in it, where it downloads from, and
     * the version the phone has (the server's ETag or Last-Modified), to fetch a newer one as it opens.
     */
    val libraryId: Long? = null,
    val onlineId: String? = null,
    val downloadUrl: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
) {
    val authorList get() = authors.lines().filter { it.isNotBlank() }
    val isbnList get() = isbns.lines().filter { it.isNotBlank() }
}

@Dao
interface PhoneBookDao {
    /** The books, the most recently read (or added) first. */
    @Query("SELECT * FROM books ORDER BY COALESCE(openedAt, addedAt) DESC")
    fun observe(): Flow<List<PhoneBook>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): PhoneBook?

    @Query("SELECT * FROM books WHERE fingerprint = :fingerprint")
    suspend fun withFingerprint(fingerprint: String): PhoneBook?

    /** Copies added from the phone, not downloaded from an online library. */
    @Query("SELECT * FROM books WHERE libraryId IS NULL")
    suspend fun addedByHand(): List<PhoneBook>

    @Query("SELECT * FROM books WHERE libraryId = :libraryId AND onlineId = :onlineId")
    suspend fun fromLibrary(libraryId: Long, onlineId: String): PhoneBook?

    @Query("UPDATE books SET libraryId = :libraryId, onlineId = :onlineId, downloadUrl = :downloadUrl, etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun setSource(id: Long, libraryId: Long, onlineId: String, downloadUrl: String, etag: String?, lastModified: String?)

    @Query("UPDATE books SET etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun setVersion(id: Long, etag: String?, lastModified: String?)

    @Query(
        "UPDATE books SET file = :file, cover = :cover, fingerprint = :fingerprint, title = :title, authors = :authors, series = :series, " +
            "seriesPosition = :seriesPosition, isbns = :isbns, etag = :etag, lastModified = :lastModified WHERE id = :id",
    )
    suspend fun replaceCopy(
        id: Long,
        file: String,
        cover: String?,
        fingerprint: String,
        title: String,
        authors: String,
        series: String?,
        seriesPosition: Double?,
        isbns: String,
        etag: String?,
        lastModified: String?,
    )

    @Query("UPDATE books SET libraryId = NULL WHERE libraryId = :libraryId")
    suspend fun forgetLibrary(libraryId: Long)

    @Query("SELECT * FROM books WHERE libraryId = :libraryId")
    suspend fun inLibrary(libraryId: Long): List<PhoneBook>

    @Query("UPDATE books SET downloadUrl = :downloadUrl WHERE id = :id")
    suspend fun setDownloadUrl(id: Long, downloadUrl: String)

    @Insert
    suspend fun add(book: PhoneBook): Long

    @Query("UPDATE books SET bookId = :bookId, bookCover = :cover WHERE id = :id")
    suspend fun link(id: Long, bookId: Long, cover: String?)

    @Query("UPDATE books SET locator = :locator, progression = :progression, openedAt = :openedAt WHERE id = :id")
    suspend fun savePosition(id: Long, locator: String, progression: Double?, openedAt: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * The reader's own books and online libraries, unlike carrel.db (copies of API responses, started again on any new
 * version), so a new version of this needs a real migration.
 */
@Database(entities = [PhoneBook::class, Library::class], version = 2, exportSchema = false)
abstract class PhoneBooksDatabase : RoomDatabase() {
    abstract fun books(): PhoneBookDao
    abstract fun libraries(): LibraryDao

    companion object {
        fun create(context: Context): PhoneBooksDatabase =
            Room.databaseBuilder(context, PhoneBooksDatabase::class.java, "phone-books.db").addMigrations(ONLINE_LIBRARIES).build()

        /** Version 2: online libraries, and where a downloaded book came from. */
        private val ONLINE_LIBRARIES = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `libraries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`url` TEXT NOT NULL, `username` TEXT, `password` TEXT)",
                )
                db.execSQL("ALTER TABLE `books` ADD COLUMN `libraryId` INTEGER")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `onlineId` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `downloadUrl` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `etag` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `lastModified` TEXT")
            }
        }
    }
}

/** Adding or opening a book on the phone failed, with a message to show the reader. */
class PhoneBookException(message: String) : Exception(message)

/**
 * The EPUBs added to this phone, and opening them with Readium. Which one was opened last is kept too, for the Home
 * button to carry on with.
 */
class PhoneBooks(private val context: Context, private val dao: PhoneBookDao, private val prefs: SharedPreferences) {
    private val http = DefaultHttpClient()
    private val assets = AssetRetriever(context.contentResolver, http)
    private val opener = PublicationOpener(DefaultPublicationParser(context, http, assets, pdfFactory = null))
    private val booksDir = File(context.filesDir, "books")
    private val coversDir = File(context.filesDir, "covers")

    val all = dao.observe()

    suspend fun get(id: Long) = dao.get(id)

    /** The book last opened in the reader (its id), if any. */
    val lastOpened get() = prefs.getLong(LAST_OPENED, 0).takeIf { it > 0 }

    /** The cover to show: the linked Carrel book's, else the file's own; null for a plain binding. */
    fun coverUrl(book: PhoneBook) = book.bookCover ?: book.cover?.let { "file://${File(coversDir, it).absolutePath}" }

    /**
     * Copies the EPUB at `uri` (picked, or opened with Carrel) into the app straight away, since the right to read it
     * doesn't last, then reads its title, authors, ISBNs, and cover. The same file added again gives the copy already
     * here.
     */
    suspend fun add(uri: Uri): PhoneBook = withContext(Dispatchers.IO) {
        val file = newFile()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
            DigestInputStream(input, digest).use { stream -> file.outputStream().use { stream.copyTo(it) } }
        } catch (e: Exception) {
            // Unreadable, or the right to read it has gone (SecurityException).
            file.delete()
            if (e !is IOException && e !is SecurityException) throw e
            throw PhoneBookException("That file couldn’t be read. Try adding it again.")
        }
        addCopy(file, digest.digest().joinToString("") { "%02x".format(it) })
    }

    /** A new, empty file for a copy, e.g. to download a book into. */
    fun newFile(): File = File(booksDir.apply { mkdirs() }, "${UUID.randomUUID()}.epub")

    /** The copy of a library's book on the phone, if it's been downloaded. */
    suspend fun fromLibrary(libraryId: Long, onlineId: String) = dao.fromLibrary(libraryId, onlineId)

    /**
     * Adds a book downloaded from an online library, remembering where it came from to fetch newer versions. A file
     * the phone already has becomes that library's copy rather than a second one.
     */
    suspend fun addDownload(download: Download.Fetched, library: Library, book: OnlineBook, url: String): PhoneBook = withContext(Dispatchers.IO) {
        val added = sameBookAddedByHand(download)?.let { copy ->
            // Another version of a book added by hand: it takes that copy's place, keeping the reader's place in it.
            replace(copy, download).also { if (it.file != copy.file) deleteCopy(copy) }
        } ?: addCopy(download.file, download.fingerprint)
        dao.setSource(added.id, library.id, book.id, url, download.etag, download.lastModified)
        dao.get(added.id)!!
    }

    /** A copy added by hand that's another version of the downloaded book (see [sameBook]), unless it's the same file. */
    private suspend fun sameBookAddedByHand(download: Download.Fetched): PhoneBook? {
        if (dao.withFingerprint(download.fingerprint) != null) return null
        val downloaded = identityOf(download.file) ?: return null
        return dao.addedByHand().firstOrNull { copy ->
            val identifiers = identityOf(File(booksDir, copy.file))?.identifiers.orEmpty()
            sameBook(downloaded, BookIdentity(identifiers, copy.title, copy.authorList.firstOrNull()))
        }
    }

    /**
     * Puts a newer version of a downloaded book in place of the old, keeping the reader's place and link. It's checked
     * before anything changes, so a broken download leaves the old copy as it was. The old copy's files stay, since it
     * may be open: the caller deletes them (see [deleteCopy]).
     */
    suspend fun replace(book: PhoneBook, download: Download.Fetched): PhoneBook = withContext(Dispatchers.IO) {
        if (download.fingerprint == book.fingerprint) {
            download.file.delete()
            dao.setVersion(book.id, download.etag, download.lastModified)
            return@withContext dao.get(book.id)!!
        }
        val details = try {
            readDetails(download.file)
        } catch (e: PhoneBookException) {
            download.file.delete()
            throw e
        }
        dao.replaceCopy(
            book.id, download.file.name, details.cover, download.fingerprint, details.title, details.authors, details.series,
            details.seriesPosition, details.isbns, download.etag, download.lastModified,
        )
        dao.get(book.id)!!
    }

    /** Deletes an old copy's file and cover, once a newer version has replaced it and it's no longer open. */
    fun deleteCopy(old: PhoneBook) {
        File(booksDir, old.file).delete()
        old.cover?.let { File(coversDir, it).delete() }
    }

    /** Books from a library being removed stay on the phone, as plain copies. */
    suspend fun forgetLibrary(libraryId: Long) = dao.forgetLibrary(libraryId)

    /** Points a library's books at its new address (see [movedDownload]), so they still get newer versions. */
    suspend fun moveLibrary(libraryId: Long, oldUrl: String, newUrl: String) =
        dao.inLibrary(libraryId).forEach { book ->
            book.downloadUrl?.let { movedDownload(it, oldUrl, newUrl) }?.let { dao.setDownloadUrl(book.id, it) }
        }

    /** Adds a copy already in files/books, or gives the copy already here if it's the same file. */
    private suspend fun addCopy(file: File, fingerprint: String): PhoneBook {
        dao.withFingerprint(fingerprint)?.let {
            file.delete()
            return it
        }
        val details = try {
            readDetails(file)
        } catch (e: PhoneBookException) {
            file.delete()
            throw e
        }
        val book = PhoneBook(
            file = file.name,
            cover = details.cover,
            fingerprint = fingerprint,
            title = details.title,
            authors = details.authors,
            series = details.series,
            seriesPosition = details.seriesPosition,
            isbns = details.isbns,
            bookId = null,
            bookCover = null,
            locator = null,
            progression = null,
            addedAt = System.currentTimeMillis(),
            openedAt = null,
        )
        return book.copy(id = dao.add(book))
    }

    /** What a copy says about itself, with its cover saved; it must open as an EPUB. */
    private suspend fun readDetails(file: File): Details {
        val publication = open(file)
        return try {
            val series = publication.metadata.belongsToSeries.firstOrNull()
            Details(
                cover = publication.coverFitting(Size(COVER_WIDTH, COVER_WIDTH * 3 / 2))?.let(::saveCover),
                title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                authors = publication.metadata.authors.joinToString("\n") { it.name },
                series = series?.name?.takeIf { it.isNotBlank() },
                seriesPosition = series?.position,
                isbns = isbnsIn(file).joinToString("\n"),
            )
        } finally {
            publication.close()
        }
    }

    private class Details(val cover: String?, val title: String, val authors: String, val series: String?, val seriesPosition: Double?, val isbns: String)

    /** Opens the book's copy to read, as the last one opened; the caller closes it. */
    suspend fun open(book: PhoneBook): Publication {
        val publication = open(File(booksDir, book.file))
        prefs.edit { putLong(LAST_OPENED, book.id) }
        return publication
    }

    suspend fun link(id: Long, book: BookDetail) = dao.link(id, book.id, book.coverUrl)

    suspend fun savePosition(id: Long, locator: String, progression: Double?) =
        dao.savePosition(id, locator, progression, System.currentTimeMillis())

    /** Deletes the book's copy and cover from the phone. Its library entry in Carrel stays. */
    suspend fun remove(book: PhoneBook) = withContext(Dispatchers.IO) {
        File(booksDir, book.file).delete()
        book.cover?.let { File(coversDir, it).delete() }
        dao.delete(book.id)
    }

    private suspend fun open(file: File): Publication {
        val asset = assets.retrieve(file).getOrElse { throw PhoneBookException(NOT_EPUB) }
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            throw PhoneBookException(NOT_EPUB)
        }
        val problem = when {
            // Bought from a shop that locks its books to its own app (DRM).
            publication.isRestricted -> "This book is locked to another app (DRM), so Carrel can’t open it."
            !publication.conformsTo(Publication.Profile.EPUB) -> NOT_EPUB
            else -> null
        }
        if (problem != null) {
            publication.close()
            throw PhoneBookException(problem)
        }
        return publication
    }

    private fun saveCover(bitmap: Bitmap): String? {
        coversDir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        return try {
            File(coversDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            name
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val LAST_OPENED = "lastOpened"
        const val COVER_WIDTH = 400
        const val NOT_EPUB = "That isn’t an EPUB Carrel can open."
    }
}

/** The ISBNs in an EPUB's package document, or none if it can't be read. */
private fun isbnsIn(file: File): List<String> = packageDocument(file)?.let(::isbnsInPackage).orEmpty()

/** An EPUB's package document (its .opf), or null if it can't be read. */
private fun packageDocument(file: File): String? = try {
    ZipFile(file).use { zip ->
        fun text(path: String) = zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { it.reader().readText() } }
        val container = text("META-INF/container.xml")
        val packagePath = container?.let { ROOTFILE.find(it)?.groupValues?.get(1) }
        packagePath?.let(::text)
    }
} catch (_: IOException) {
    null
}

/** What tells one book from another: its identifiers (Calibre's book id, an ISBN, and so on), title, and first author. */
data class BookIdentity(val identifiers: Set<String>, val title: String, val firstAuthor: String?)

private fun identityOf(file: File) = packageDocument(file)?.let(::identityIn)

/** A book's identity from its package document. */
fun identityIn(opf: String) = BookIdentity(
    identifiers = IDENTIFIER.findAll(opf).map { it.groupValues[1].trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
    title = TITLE.find(opf)?.groupValues?.get(1)?.trim().orEmpty(),
    firstAuthor = CREATOR.find(opf)?.groupValues?.get(1)?.trim(),
)

/**
 * Whether two copies are versions of the same book: an identifier in common (Calibre keeps its own in every copy it
 * exports), or else the same title and first author, ignoring case and punctuation.
 */
fun sameBook(a: BookIdentity, b: BookIdentity): Boolean {
    if (a.identifiers.any { it in b.identifiers }) return true
    fun plain(text: String?) = text.orEmpty().lowercase().filter(Char::isLetterOrDigit)
    return plain(a.title).isNotEmpty() && plain(a.title) == plain(b.title) &&
        plain(a.firstAuthor).isNotEmpty() && plain(a.firstAuthor) == plain(b.firstAuthor)
}

private val TITLE = Regex("""<dc:title\b[^>]*>([^<]*)</dc:title>""", RegexOption.IGNORE_CASE)
private val CREATOR = Regex("""<dc:creator\b[^>]*>([^<]*)</dc:creator>""", RegexOption.IGNORE_CASE)

/**
 * The ISBNs among an EPUB package document's identifiers (dc:identifier), in order, e.g. urn:isbn:9780141439518 or
 * 978-0-14-143951-8. Readium only keeps the one identifier the package names as its own, often a UUID.
 */
fun isbnsInPackage(opf: String): List<String> =
    IDENTIFIER.findAll(opf)
        .mapNotNull { match -> ISBN_TEXT.matchEntire(match.groupValues[1].trim())?.let { validIsbn(it.value) } }
        .distinct()
        .toList()

private val ROOTFILE = Regex("""full-path\s*=\s*["']([^"']+)["']""")
private val IDENTIFIER = Regex("""<(?:dc:)?identifier\b[^>]*>([^<]*)</(?:dc:)?identifier>""", RegexOption.IGNORE_CASE)
private val ISBN_TEXT = Regex("""(?:urn:isbn:|isbn:?\s*)?\d[\d -]{8,15}[\dX]""", RegexOption.IGNORE_CASE)

/** How the full list of books on the phone is grouped. */
enum class Grouping(val label: String) {
    Recent("Recent"), Title("Title"), Author("Author"), Series("Series"),
}

/**
 * The books on the phone in groups, each with its heading (null for the one ungrouped list): most recently read first;
 * by title, under each first letter; by first author; or by series, in series order, with books in none last.
 */
fun grouped(books: List<PhoneBook>, grouping: Grouping): List<Pair<String?, List<PhoneBook>>> {
    val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER, PhoneBook::title)
    return when (grouping) {
        Grouping.Recent -> listOf(null to books.sortedByDescending { it.openedAt ?: it.addedAt })
        Grouping.Title -> books.sortedWith(byTitle)
            .groupBy { book -> book.title.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#" }
            .toList()
        Grouping.Author -> books.groupBy { it.authorList.firstOrNull() ?: UNKNOWN_AUTHOR }
            .toSortedMap(compareBy<String> { it == UNKNOWN_AUTHOR }.then(String.CASE_INSENSITIVE_ORDER))
            .map { (author, group) -> author to group.sortedWith(byTitle) }
        Grouping.Series -> books.groupBy { it.series ?: NO_SERIES }
            .toSortedMap(compareBy<String> { it == NO_SERIES }.then(String.CASE_INSENSITIVE_ORDER))
            .map { (series, group) -> series to group.sortedWith(compareBy<PhoneBook, Double?>(nullsLast()) { it.seriesPosition }.then(byTitle)) }
    }
}

private const val UNKNOWN_AUTHOR = "Unknown author"
private const val NO_SERIES = "Not in a series"
