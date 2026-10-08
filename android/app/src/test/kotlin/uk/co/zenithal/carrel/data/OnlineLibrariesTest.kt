package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLibrariesTest {
    // RFC 7616, section 3.9.1.
    private val challenge = mapOf(
        "realm" to "http-auth@example.org",
        "qop" to "auth, auth-int",
        "nonce" to "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v",
        "opaque" to "FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS",
    )

    private fun answer(params: Map<String, String>) =
        digestAuthorization(params, "GET", "/dir/index.html", "Mufasa", "Circle of Life", "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ")

    @Test
    fun answersDigestChallengesAsTheRfcDoes() {
        val md5 = answer(challenge + ("algorithm" to "MD5"))!!
        assertTrue(md5, "response=\"8ca523f5e9506fed4657c9700eebdbec\"" in md5)
        assertTrue(md5, "qop=auth, nc=00000001" in md5)
        assertTrue(md5, "opaque=\"FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS\"" in md5)
        val sha256 = answer(challenge + ("algorithm" to "SHA-256"))!!
        assertTrue(sha256, "response=\"753927fa0e85d155564e2e272a28d1802ca10daf4496794697cf8db5856cb6c1\"" in sha256)
    }

    @Test
    fun turnsDownChallengesItCantAnswer() {
        assertNull(answer(challenge + ("algorithm" to "SHA-512-256")))
        assertNull(answer(challenge + ("qop" to "auth-int")))
        assertNull(answer(challenge - "nonce"))
    }

    @Test
    fun findsTheNewestBooks() {
        // Calibre's content server: "By Newest", with no sort relation.
        val calibre = listOf(
            Section("By Title", "http://nas:8081/opds/navcatalog/4f7469746c65?library_id=books"),
            Section("By Newest", "http://nas:8081/opds/navcatalog/4f6e6577657374?library_id=books"),
        )
        assertEquals("By Newest", newestSection(calibre)?.title)
        // A catalogue that marks it, and Calibre-Web's "Recently Added Books".
        assertEquals("Fresh", newestSection(listOf(Section("A–Z", "/a"), Section("Fresh", "/f", setOf("http://opds-spec.org/sort/new"))))?.title)
        assertEquals("Recently Added Books", newestSection(listOf(Section("Authors", "/a"), Section("Recently Added Books", "/new")))?.title)
        assertNull(newestSection(listOf(Section("Authors", "/a"), Section("Series", "/s"))))
    }

    @Test
    fun makesSearchTemplatesAbsolute() {
        assertEquals(
            "http://nas:8081/opds/search/{searchTerms}?library_id=books",
            resolveTemplate("/opds/search/{searchTerms}?library_id=books", "http://nas:8081/opds"),
        )
        assertEquals(
            "http://nas:8081/opds/search/{searchTerms}?library_id=books",
            resolveTemplate("/opds/search/%7BsearchTerms%7D?library_id=books", "http://nas:8081/opds"),
        )
        // Optional parameters go; required ones it can't fill make it unusable.
        assertEquals("http://web:8083/opds/search?q={searchTerms}", resolveTemplate("/opds/search?q={searchTerms}{&page?}", "http://web:8083/opds"))
        assertNull(resolveTemplate("/opds/search?q={searchTerms}&lang={language}", "http://web:8083/opds"))
    }

    @Test
    fun findsAnAtomFeedsSearchLink() {
        // As Calibre's content server writes it.
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <link rel="start" href="/opds?library_id=books" type="application/atom+xml;profile=opds-catalog"/>
              <link rel="search" title="Search" href="/opds/search/{searchTerms}?library_id=books&amp;x=1" type="application/atom+xml"/>
            </feed>
        """
        assertEquals("/opds/search/{searchTerms}?library_id=books&x=1" to "application/atom+xml", atomSearchLink(atom))
        assertNull(atomSearchLink("""<feed><link rel="start" href="/opds"/></feed>"""))
    }

    @Test
    fun readsOpenSearchDescriptions() {
        val description = """
            <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
              <Url type="text/html" template="/search?q={searchTerms}"/>
              <Url type="application/atom+xml" template="/opds/search/{searchTerms}?a=1&amp;b=2"/>
            </OpenSearchDescription>
        """
        assertEquals("/opds/search/{searchTerms}?a=1&b=2", openSearchTemplate(description))
    }
}
