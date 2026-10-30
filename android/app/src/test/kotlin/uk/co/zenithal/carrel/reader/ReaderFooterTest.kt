package uk.co.zenithal.carrel.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFooterTest {
    @Test
    fun countsThePagesAfterThoseShowing() {
        assertEquals("4 pages left in chapter", pagesLeftText(ChapterPlace("Chapter 1", 3, 5, 9)))
        assertEquals("1 page left in chapter", pagesLeftText(ChapterPlace("Chapter 1", 8, 8, 9)))
        assertEquals("Last page in chapter", pagesLeftText(ChapterPlace("Chapter 1", 8, 9, 9)))
    }

    @Test
    fun showsTimeLeftToTheMinute() {
        assertEquals("Less than a minute", durationText(20.0))
        assertEquals("1 min", durationText(80.0))
        assertEquals("59 min", durationText(59 * 60.0))
        assertEquals("2 h", durationText(2 * 3600.0 + 10))
        assertEquals("2 h 5 min", durationText(2 * 3600.0 + 5 * 60))
    }
}
