package uk.co.zenithal.carrel.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.Feed
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * An online library: an OPDS catalogue such as Calibre's content server or Calibre-Web. It belongs to the phone, like
 * its books. The password is encrypted with a key kept in the Android Keystore (see [Secrets]).
 */
@Entity(tableName = "libraries")
data class Library(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The catalogue's address, e.g. http://192.168.1.20:8080/opds. */
    val url: String,
    val username: String?,
    val password: String?,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<Library>>

    @Query("SELECT * FROM libraries WHERE id = :id")
    suspend fun get(id: Long): Library?

    @Insert
    suspend fun add(library: Library): Long

    @Update
    suspend fun update(library: Library)

    @Query("DELETE FROM libraries WHERE id = :id")
    suspend fun delete(id: Long)
}

/** A catalogue page: its sections to browse, its books, the next page, and how to search it. */
data class OnlineFeed(
    val title: String,
    val sections: List<Section>,
    val books: List<OnlineBook>,
    val next: String?,
    /** The search address, with SEARCH_TERMS where the words go. */
    val search: String?,
)

data class Section(val title: String, val url: String, val rels: Set<String> = emptySet())

/** A book in a catalogue. `epub` is where to download it, null when it isn't offered as an EPUB. */
data class OnlineBook(
    val id: String,
    val title: String,
    val authors: List<String>,
    val series: String?,
    val seriesPosition: Double?,
    val summary: String?,
    val cover: String?,
    val thumbnail: String?,
    val epub: String?,
)

/** A failed request to an online library, with a message to show. */
class LibraryException(message: String) : Exception(message)

/** The result of downloading a book: unchanged since the version the phone has, or a new copy in `file`. */
sealed interface Download {
    data object Unchanged : Download
    data class Fetched(val file: File, val fingerprint: String, val etag: String?, val lastModified: String?) : Download
}

/**
 * Talks to online libraries with their own HTTP client, never the Carrel API's, so a library never sees the reader's
 * Carrel session. Each library's login is only ever sent back to its own server (scheme, host, and port), after it
 * asks for one (Basic or Digest).
 */
class OnlineLibraries(private val dao: LibraryDao, private val secrets: Secrets) {
    /** Logins by origin (scheme://host:port), for the authenticator. */
    private val logins = ConcurrentHashMap<String, Pair<String, String>>()
    private val feeds = ConcurrentHashMap<String, OnlineFeed>()

    val http: OkHttpClient = OkHttpClient.Builder()
        .authenticator(LoginAuthenticator { url -> logins[origin(url)] })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val all: Flow<List<Library>> = dao.observe()

    suspend fun get(id: Long) = dao.get(id)

    /** Makes the libraries' logins known to the client (and to cover images), e.g. as the app starts. */
    suspend fun load() = dao.observe().first().forEach(::remember)

    /** Checks the catalogue answers with these details, then saves it (as new, or in place of `id`). */
    suspend fun save(id: Long?, name: String, url: String, username: String?, password: String?): Library {
        val address = url.trim().let { if ("://" in it) it else "http://$it" }
        address.toHttpUrlOrNull() ?: throw LibraryException("That isn’t a web address. It looks like http://192.168.1.20:8080/opds.")
        val library = Library(
            id = id ?: 0,
            name = name.trim().ifEmpty { address.toHttpUrlOrNull()!!.host },
            url = address,
            username = username?.trim()?.ifEmpty { null },
            password = password?.ifEmpty { null }?.let(secrets::encrypt),
        )
        remember(library)
        try {
            feed(library, address, fresh = true)
        } catch (e: LibraryException) {
            // Back to the login that worked before, if any.
            id?.let { dao.get(it) }?.let(::remember)
            throw e
        }
        return if (id == null) library.copy(id = dao.add(library)) else library.also { dao.update(it) }
    }

    suspend fun remove(library: Library) {
        dao.delete(library.id)
        logins.remove(origin(library.url.toHttpUrlOrNull() ?: return))
    }

    /** The password as saved, to show in the form; null if there's none, or the key to read it has gone. */
    fun password(library: Library) = library.password?.let(secrets::decrypt)

    /** A catalogue page, kept for the session unless `fresh`. */
    suspend fun feed(library: Library, url: String = library.url, fresh: Boolean = false): OnlineFeed {
        if (!fresh) feeds[url]?.let { return it }
        // Reading the page, as well as asking for it, is network work.
        val feed = withContext(Dispatchers.IO) {
            get(http, url).use { r ->
                val bytes = r.body.bytes()
                val finalUrl = r.request.url.toString()
                val base = Url(finalUrl) as? AbsoluteUrl ?: throw LibraryException(NOT_A_CATALOGUE)
                val json = r.header("Content-Type").orEmpty().contains("json")
                val parsed = try {
                    if (json) OPDS2Parser.parse(bytes, base).feed else OPDS1Parser.parse(bytes, base).feed
                } catch (_: Exception) {
                    null
                } ?: throw LibraryException(NOT_A_CATALOGUE)
                // Readium doesn't keep an Atom feed's search template as written, so it's read from the feed itself.
                val search = if (json) {
                    parsed.links.firstOrNull { "search" in it.rels }?.let { it.href.toString() to it.mediaType?.toString() }
                } else {
                    atomSearchLink(String(bytes, Charsets.UTF_8))
                }
                toOnlineFeed(parsed, finalUrl, library, search?.let { (href, type) -> searchTemplate(href, type, finalUrl) })
            }
        }
        feeds[url] = feed
        return feed
    }

    /** Where a catalogue search for `words` is; null if the catalogue can't be searched. */
    fun searchUrl(feed: OnlineFeed, words: String): String? =
        feed.search?.replace(SEARCH_TERMS, URLEncoder.encode(words.trim(), "UTF-8").replace("+", "%20"))

    /** Downloads a book into `into` (a temporary file), unless it's unchanged since the version with `etag` or `lastModified`. */
    suspend fun download(url: String, into: File, etag: String?, lastModified: String?): Download =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                etag?.let { header("If-None-Match", it) }
                lastModified?.let { header("If-Modified-Since", it) }
            }.build()
            val response = try {
                http.newCall(request).execute()
            } catch (_: IOException) {
                throw LibraryException(CANT_REACH)
            }
            response.use { r ->
                if (r.code == 304) return@withContext Download.Unchanged
                if (!r.isSuccessful) throw LibraryException(messageFor(r.code))
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    r.body.byteStream().use { input ->
                        into.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (_: IOException) {
                    into.delete()
                    throw LibraryException(CANT_REACH)
                }
                Download.Fetched(into, digest.digest().joinToString("") { "%02x".format(it) }, r.header("ETag"), r.header("Last-Modified"))
            }
        }

    private fun remember(library: Library) {
        val url = library.url.toHttpUrlOrNull() ?: return
        val user = library.username
        val password = library.password?.let(secrets::decrypt)
        if (user != null && password != null) logins[origin(url)] = user to password else logins.remove(origin(url))
    }

    /** A successful response, on the IO dispatcher the caller is on. */
    private fun get(client: OkHttpClient, url: String): Response {
        val response = try {
            client.newCall(Request.Builder().url(url).header("Accept", "application/atom+xml, application/opds+json, */*").build()).execute()
        } catch (_: IOException) {
            throw LibraryException(CANT_REACH)
        } catch (_: IllegalArgumentException) {
            throw LibraryException(NOT_A_CATALOGUE)
        }
        if (!response.isSuccessful) {
            response.close()
            throw LibraryException(messageFor(response.code))
        }
        return response
    }

    /** The search address: an OpenSearch description (Calibre-Web) is fetched for the template inside it. */
    private fun searchTemplate(href: String, type: String?, base: String): String? {
        if (type?.contains("opensearchdescription") != true) return resolveTemplate(href, base)
        val description = try {
            get(http, resolve(href, base) ?: return null).use { it.body.string() }
        } catch (_: LibraryException) {
            return null
        }
        return openSearchTemplate(description)?.let { resolveTemplate(it, base) }
    }

    private fun toOnlineFeed(feed: Feed, base: String, library: Library, search: String?): OnlineFeed = OnlineFeed(
        title = feed.metadata.title.ifBlank { library.name },
        sections = feed.navigation.mapNotNull { link ->
            resolve(link.href.toString(), base)?.let { Section(link.title?.takeIf { it.isNotBlank() } ?: "Untitled", it, link.rels) }
        },
        books = feed.publications.mapNotNull { publication ->
            val metadata = publication.metadata
            val epub = publication.links.firstOrNull { link ->
                link.rels.any { it.startsWith(ACQUISITION) } && link.mediaType?.toString()?.startsWith(EPUB) == true
            }?.let { resolve(it.href.toString(), base) }
            val images = publication.images
            val thumbnail = images.firstOrNull { link -> link.rels.any { "thumbnail" in it } } ?: images.firstOrNull()
            val cover = images.firstOrNull { link -> link.rels.none { "thumbnail" in it } } ?: thumbnail
            val series = metadata.belongsToSeries.firstOrNull()
            OnlineBook(
                id = metadata.identifier ?: epub ?: return@mapNotNull null,
                title = metadata.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                authors = metadata.authors.map { it.name },
                series = series?.name,
                seriesPosition = series?.position,
                summary = metadata.description?.let(::plainText)?.takeIf { it.isNotBlank() },
                cover = cover?.let { resolve(it.href.toString(), base) },
                thumbnail = thumbnail?.let { resolve(it.href.toString(), base) },
                epub = epub,
            )
        },
        next = feed.links.firstOrNull { "next" in it.rels }?.let { resolve(it.href.toString(), base) },
        search = search,
    )

    companion object {
        const val SEARCH_TERMS = "{searchTerms}"
        private const val ACQUISITION = "http://opds-spec.org/acquisition"
        private const val EPUB = "application/epub+zip"
        const val CANT_REACH = "Can’t reach this library. If it’s on your home network, check you’re connected to it."
        private const val NOT_A_CATALOGUE = "That address isn’t an OPDS catalogue. For Calibre’s server it usually ends in /opds."

        private fun messageFor(code: Int) = when (code) {
            401, 403 -> "The library turned down the username or password."
            404 -> "Nothing was found at that address. For Calibre’s server it usually ends in /opds."
            else -> "The library had a problem (error $code). Try again later."
        }

        fun origin(url: HttpUrl) = "${url.scheme}://${url.host}:${url.port}"
    }
}

/** A link's address made absolute against the page it's on; null if it isn't a usable address. */
internal fun resolve(href: String, base: String): String? =
    try {
        URI(base).resolve(URI(href.replace(" ", "%20"))).toString().takeIf { it.startsWith("http") }
    } catch (_: Exception) {
        null
    }

/**
 * A book's download address after its library moved from `oldUrl` to `newUrl` (another host, port, or scheme); null if
 * it isn't on the old library's server, or the server hasn't changed.
 */
internal fun movedDownload(downloadUrl: String, oldUrl: String, newUrl: String): String? {
    val download = downloadUrl.toHttpUrlOrNull() ?: return null
    val old = oldUrl.toHttpUrlOrNull() ?: return null
    val new = newUrl.toHttpUrlOrNull() ?: return null
    if (OnlineLibraries.origin(download) != OnlineLibraries.origin(old) || OnlineLibraries.origin(old) == OnlineLibraries.origin(new)) return null
    return download.newBuilder().scheme(new.scheme).host(new.host).port(new.port).build().toString()
}

/** A search template made absolute, keeping {searchTerms} for the words and dropping optional parameters. */
internal fun resolveTemplate(template: String, base: String): String? {
    val placeholder = "CARRELSEARCHTERMS"
    val withWords = template
        .replace(OnlineLibraries.SEARCH_TERMS, placeholder)
        .replace("%7BsearchTerms%7D", placeholder, ignoreCase = true)
        .replace(Regex("""\{[^\}]*\?\}"""), "")
    if (Regex("""\{[^\}]*\}""").containsMatchIn(withWords)) return null
    return resolve(withWords, base)?.replace(placeholder, OnlineLibraries.SEARCH_TERMS)
}

/** An Atom feed's search link: its address (or template) and type. */
internal fun atomSearchLink(atom: String): Pair<String, String?>? {
    val link = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(atom).map { it.value }
        .firstOrNull { Regex("""\brel\s*=\s*["']search["']""").containsMatchIn(it) } ?: return null
    fun attribute(name: String) = Regex("""\b$name\s*=\s*["']([^"']*)["']""").find(link)?.groupValues?.get(1)?.replace("&amp;", "&")
    return (attribute("href") ?: return null) to attribute("type")
}

/** The Atom results template in an OpenSearch description, as Calibre-Web serves. */
internal fun openSearchTemplate(description: String): String? =
    Regex("""<Url\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(description).map { it.value }
        .sortedByDescending { "atom" in it.lowercase() }
        .firstNotNullOfOrNull { Regex("""template\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
        ?.replace("&amp;", "&")

/**
 * The catalogue's newest books: a section the catalogue marks as sorted by newest, or Calibre's "By Newest", or one
 * titled so (Calibre-Web's "Recently Added").
 */
fun newestSection(sections: List<Section>): Section? =
    sections.firstOrNull { "http://opds-spec.org/sort/new" in it.rels }
        ?: sections.firstOrNull { "/navcatalog/4f6e6577657374" in it.url }
        ?: sections.firstOrNull { NEWEST.containsMatchIn(it.title) }

private val NEWEST = Regex("""\b(newest|recently added|new books|latest)\b""", RegexOption.IGNORE_CASE)

/** HTML in a book's summary, as plain paragraphs. */
private fun plainText(html: String) = html
    .replace(Regex("""<\s*(br|/p|/div)\s*/?>""", RegexOption.IGNORE_CASE), "\n\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

/**
 * Answers a library's request for a login (Basic or Digest), only for the server it belongs to, and only once: a login
 * already turned down isn't sent again (except a Digest one whose nonce went stale).
 */
private class LoginAuthenticator(private val loginFor: (HttpUrl) -> Pair<String, String>?) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // The address after any redirects, so a login is never sent to another server.
        val request = response.request
        val (user, password) = loginFor(request.url) ?: return null
        val tried = request.header("Authorization") != null
        val challenges = response.challenges()
        val digest = challenges.firstOrNull { it.scheme.equals("Digest", ignoreCase = true) }
        val header = when {
            digest != null -> {
                val stale = digest.authParams["stale"].equals("true", ignoreCase = true)
                if (tried && !stale) return null
                if (priorResponses(response) >= 3) return null
                val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
                digestAuthorization(digest.authParams.filterKeys { it != null }.mapKeys { it.key!!.lowercase() }, request.method, uri, user, password, newCnonce())
            }
            challenges.any { it.scheme.equals("Basic", ignoreCase = true) } && !tried -> Credentials.basic(user, password, Charsets.UTF_8)
            else -> null
        } ?: return null
        return request.newBuilder().header("Authorization", header).build()
    }

    private fun priorResponses(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun newCnonce(): String = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
}

/**
 * The Authorization header answering a Digest challenge (RFC 7616): MD5 or SHA-256, either -sess form, with qop=auth or
 * none. `params` are the challenge's, with lowercase names; `uri` is the request target as sent. Null for a challenge
 * it can't answer (another algorithm, or only auth-int).
 */
fun digestAuthorization(params: Map<String, String>, method: String, uri: String, user: String, password: String, cnonce: String, nc: String = "00000001"): String? {
    val realm = params["realm"] ?: return null
    val nonce = params["nonce"] ?: return null
    val algorithm = params["algorithm"] ?: "MD5"
    val hashName = when (algorithm.uppercase().removeSuffix("-SESS")) {
        "MD5" -> "MD5"
        "SHA-256" -> "SHA-256"
        else -> return null
    }
    fun h(text: String) = MessageDigest.getInstance(hashName).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    val qops = params["qop"]?.split(',')?.map { it.trim() }
    val qop = when {
        qops == null -> null
        "auth" in qops -> "auth"
        else -> return null
    }
    var ha1 = h("$user:$realm:$password")
    if (algorithm.uppercase().endsWith("-SESS")) ha1 = h("$ha1:$nonce:$cnonce")
    val ha2 = h("$method:$uri")
    val response = if (qop != null) h("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else h("$ha1:$nonce:$ha2")
    return buildString {
        append("Digest username=\"$user\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", algorithm=$algorithm, response=\"$response\"")
        if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
        params["opaque"]?.let { append(", opaque=\"$it\"") }
    }
}

/**
 * Encrypts saved passwords with an AES key kept in the Android Keystore, which never leaves it. If the key is lost (a
 * restored phone, say), the password can't be read and the reader enters it again.
 */
class Secrets {
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(text: String): String? = try {
        val sealed = Base64.decode(text, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, IV_LENGTH)) }
        String(cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "library-passwords"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
