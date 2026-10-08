package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val hosts = setOf("carrel.zenithal.co.uk", "localhost:5173")
    private fun link(path: String) = linkDestination("https://carrel.zenithal.co.uk$path", hosts)

    @Test
    fun opensTheWebsitesPages() {
        assertEquals(Destination.Book("/books/12"), link("/books/12"))
        assertEquals(Destination.Book("/books/hardcover/427"), link("/books/hardcover/427"))
        assertEquals(Destination.Book("/books/openlibrary/OL45804W"), link("/books/openlibrary/OL45804W"))
        assertEquals(Destination.Series(3, 427), link("/series/hardcover/3?book=427"))
        assertEquals(Destination.Series(3, null), link("/series/hardcover/3"))
        assertEquals(Destination.Genres, link("/genres"))
        assertEquals(Destination.Genre("science-fiction"), link("/genres/science-fiction/"))
        assertEquals(Destination.Search("le guin"), link("/search?q=le+guin"))
        assertEquals(Destination.Search(null), link("/search"))
        assertEquals(Destination.Reader("Some_Reader"), link("/@Some_Reader"))
        assertEquals(Destination.ReaderShelf("reader", ReadingStatus.WantToRead), link("/@reader/shelves/want-to-read"))
        assertEquals(Destination.OwnShelf(ReadingStatus.Read), link("/shelves/read"))
    }

    @Test
    fun acceptsTheLocalWebsite() {
        assertEquals(Destination.Book("/books/12"), linkDestination("http://localhost:5173/books/12", hosts))
        assertNull(linkDestination("http://localhost:8080/books/12", hosts))
    }

    @Test
    fun turnsAwayAnythingElse() {
        assertNull(link("/privacy"))
        assertNull(link("/books/12/../../account"))
        assertNull(link("/books/hardcover/1%2F..%2Faccount"))
        assertNull(link("/books/openlibrary/OL1W%2F"))
        assertNull(link("/@a"))
        assertNull(link("/@reader/shelves/favourites"))
        assertNull(link("/genres/Science%20Fiction"))
        assertNull(linkDestination("https://example.com/books/12", hosts))
        assertNull(linkDestination("javascript:alert(1)", hosts))
    }

    @Test
    fun checksIsbns() {
        assertEquals("9780756404741", validIsbn("978-0-7564-0474-1"))
        assertEquals("080442957X", validIsbn("0-8044-2957-x"))
        assertEquals("0575079967", validIsbn("0575079967"))
        assertNull(validIsbn("9780756404742"))
        assertNull(validIsbn("186074"))
    }

    @Test
    fun opensSharedCarrelLinks() {
        assertEquals(Destination.Book("/books/12"), sharedDestination("Look at this https://carrel.zenithal.co.uk/books/12", null, hosts))
    }

    @Test
    fun findsSharedIsbns() {
        assertEquals(
            Destination.Isbn("9780756404741", "The Name of the Wind"),
            sharedDestination("https://www.amazon.co.uk/Name-Wind/dp/0756404746 ISBN 978-0-7564-0474-1", "The Name of the Wind", hosts),
        )
        // Amazon's id for a printed book is its ISBN-10; a Kindle book's (B0…) isn't.
        assertEquals(
            Destination.Isbn("0575079967", "Way Kings Stormlight Archive Book"),
            sharedDestination("https://www.amazon.co.uk/Way-Kings-Stormlight-Archive-Book/dp/0575079967/ref=sr_1_1", null, hosts),
        )
        assertEquals(
            Destination.Search("Way Kings Stormlight Archive Book"),
            sharedDestination("https://www.amazon.co.uk/Way-Kings-Stormlight-Archive-Book/dp/B003P2WO5E", null, hosts),
        )
    }

    @Test
    fun searchesForSharedTitles() {
        assertEquals(
            Destination.Search("The Name of the Wind Patrick Rothfuss"),
            sharedDestination("https://www.goodreads.com/book/show/186074", "The Name of the Wind (The Kingkiller Chronicle, #1) by Patrick Rothfuss | Goodreads", hosts),
        )
        assertEquals(Destination.Search("The Name of the Wind"), sharedDestination("https://www.goodreads.com/book/show/186074.The_Name_of_the_Wind", null, hosts))
        assertEquals(Destination.Search("the name of the wind"), sharedDestination("https://hardcover.app/books/the-name-of-the-wind", null, hosts))
        assertEquals(Destination.Search("Piranesi"), sharedDestination("  Piranesi ", null, hosts))
        // A Goodreads id isn't an ISBN, even with ten digits.
        assertEquals(Destination.Search("piranesi"), sharedDestination("https://www.goodreads.com/book/show/1234567890-piranesi", null, hosts))
        assertNull(sharedDestination("https://example.com/", null, hosts))
        assertNull(sharedDestination(null, null, hosts))
    }
}
