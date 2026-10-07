package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `series positions read as on a spine`() {
        assertEquals("1", seriesPosition(1.0))
        assertEquals("1.5", seriesPosition(1.5))
        assertEquals("10", seriesPosition(10.0))
        assertEquals("0.25", seriesPosition(0.25))
        assertNull(seriesPosition(null))
    }

    @Test
    fun `a subtitle repeating the end of the title is hidden`() {
        assertNull(displaySubtitle("Mistborn: The Final Empire", "The Final Empire"))
        assertNull(displaySubtitle("Mistborn: The Final Empire", "the final empire"))
        assertEquals("A Mistborn Novel", displaySubtitle("The Well of Ascension", "A Mistborn Novel"))
        assertNull(displaySubtitle("Title", ""))
    }

    @Test
    fun `names use the Oxford comma`() {
        assertEquals("", listNames(emptyList()))
        assertEquals("A", listNames(listOf("A")))
        assertEquals("A and B", listNames(listOf("A", "B")))
        assertEquals("A, B, and C", listNames(listOf("A", "B", "C")))
    }

    @Test
    fun `other credits group by role`() {
        val credits = otherCredits(
            listOf(
                Contributor(1, "Author", "author"),
                Contributor(2, "T One", "translator"),
                Contributor(3, "T Two", "translator"),
                Contributor(4, "Someone", "colourist"),
            ),
        )
        assertEquals(listOf("Translated by T One and T Two", "Colourist: Someone"), credits)
    }

    @Test
    fun `dates read day month year`() {
        assertEquals("17 Jul 2006", formatDate("2006-07-17"))
        assertNull(formatDate(null))
        assertNull(formatDate("not a date"))
    }

    @Test
    fun durations() {
        assertEquals("45 m", formatDuration(45 * 60))
        assertEquals("12 h 3 m", formatDuration(12 * 3600 + 3 * 60 + 10))
    }

    @Test
    fun `genre addresses match the API's`() {
        assertEquals("science-fiction", genreSlug("Science Fiction"))
        assertEquals("self-help", genreSlug("Self-Help"))
        assertEquals("comics-and-graphic-novels", genreSlug("Comics & Graphic Novels"))
        assertEquals("childrens", genreSlug("Children's"))
        assertEquals("", genreSlug("—"))
    }
}
