package uk.co.zenithal.carrel.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChaptersTest {
    @Test
    fun countsPagesInAChapterThatIsItsOwnFile() {
        val entries = listOf(ContentsEntry("Prologue", 1, null), ContentsEntry("Chapter 1", 2, null))
        val layout = FileLayout(2, 9, emptyMap(), 9)
        assertEquals(ChapterPlace("Chapter 1", 3, 3, 9), chapterPlace(entries, layout, 2, 1))
    }

    @Test
    fun showsNothingBeforeTheFirstEntry() {
        val entries = listOf(ContentsEntry("Chapter 1", 2, null))
        assertNull(chapterPlace(entries, FileLayout(0, 1, emptyMap(), 1), 0, 1))
    }

    @Test
    fun findsChaptersThatShareAFile() {
        val entries = listOf(ContentsEntry("Chapter 1", 0, "one"), ContentsEntry("Chapter 2", 0, "two"), ContentsEntry("Chapter 3", 0, "three"))
        val layout = FileLayout(0, 30, mapOf(0 to 0, 1 to 10, 2 to 25), 30)
        assertEquals(ChapterPlace("Chapter 1", 10, 10, 10), chapterPlace(entries, layout, 9, 1))
        assertEquals(ChapterPlace("Chapter 2", 1, 1, 15), chapterPlace(entries, layout, 10, 1))
        assertEquals(ChapterPlace("Chapter 3", 5, 5, 5), chapterPlace(entries, layout, 29, 1))
    }

    @Test
    fun namesTheLeftPageWhenTheRightStartsAChapter() {
        val entries = listOf(ContentsEntry("Chapter 2", 0, "two"), ContentsEntry("Chapter 3", 0, "three"))
        val layout = FileLayout(0, 10, mapOf(0 to 0, 1 to 9), 20)
        assertEquals(ChapterPlace("Chapter 2", 9, 9, 9), chapterPlace(entries, layout, 8, 2))
        assertEquals(ChapterPlace("Chapter 2", 7, 8, 9), chapterPlace(entries, layout, 6, 2))
    }

    @Test
    fun endsOnAPageOnItsOwnWhenTheTextFillsAnOddNumber() {
        val layout = FileLayout(0, 4, emptyMap(), 7)
        assertEquals(ChapterPlace("Chapter 1", 7, 7, 7), chapterPlace(listOf(ContentsEntry("Chapter 1", 0, null)), layout, 6, 2))
    }

    @Test
    fun prefersTheChapterOverThePartStartingWithIt() {
        val entries = listOf(ContentsEntry("Part One", 3, null), ContentsEntry("Chapter 1", 3, null))
        assertEquals("Chapter 1", chapterPlace(entries, FileLayout(3, 5, emptyMap(), 5), 0, 1)?.title)
    }

    @Test
    fun carriesAChapterOnIntoTheNextFile() {
        val entries = listOf(ContentsEntry("Chapter 1", 0, null), ContentsEntry("Chapter 2", 1, "two"))
        val layout = FileLayout(1, 8, mapOf(1 to 4), 8)
        assertEquals(ChapterPlace("Chapter 1", 2, 2, 4), chapterPlace(entries, layout, 1, 1))
    }

    @Test
    fun takesTheLastEntryOfAnEarlierFile() {
        val entries = listOf(ContentsEntry("Chapter 1", 0, "one"), ContentsEntry("Chapter 2", 0, "two"), ContentsEntry("Chapter 3", 2, null))
        assertEquals("Chapter 2", chapterPlace(entries, FileLayout(1, 3, emptyMap(), 3), 0, 1)?.title)
    }

    @Test
    fun writesOneOrTwoPages() {
        assertEquals("(Page 3 / 9)", pagesText(ChapterPlace("Chapter 1", 3, 3, 9)))
        assertEquals("(Pages 5–6 / 20)", pagesText(ChapterPlace("Prologue", 5, 6, 20)))
    }
}
