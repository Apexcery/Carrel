package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    private fun entry(unit: ProgressUnit?, value: Double?, percent: Double? = null, total: Int? = null) = LibraryEntry(
        id = 1, bookId = 1, status = ReadingStatus.Reading, rating = null, editionId = null,
        progressUnit = unit, progressValue = value, progressPercent = percent, progressTotal = total,
        addedAt = "", updatedAt = "", reads = emptyList(),
    )

    @Test
    fun `progress values parse as the website does`() {
        assertEquals(210.0, parseProgressValue(ProgressUnit.Page, " 210 "))
        assertEquals(12.5, parseProgressValue(ProgressUnit.Percent, "12.5"))
        assertNull(parseProgressValue(ProgressUnit.Percent, "101"))
        assertNull(parseProgressValue(ProgressUnit.Page, "-3"))
        assertNull(parseProgressValue(ProgressUnit.Page, ""))
        assertNull(parseProgressValue(ProgressUnit.Page, "NaN"))
        assertNull(parseProgressValue(ProgressUnit.Page, "twelve"))
        assertEquals(13_500.0, parseProgressValue(ProgressUnit.Seconds, "3:45"))
        assertEquals(10_800.0, parseProgressValue(ProgressUnit.Seconds, "3"))
        assertNull(parseProgressValue(ProgressUnit.Seconds, "3:456"))
        assertNull(parseProgressValue(ProgressUnit.Seconds, "3.5"))
    }

    @Test
    fun `saved values show as they were typed`() {
        assertEquals("210", formatProgressValue(ProgressUnit.Page, 210.0))
        assertEquals("12.5", formatProgressValue(ProgressUnit.Percent, 12.5))
        assertEquals("3:45", formatProgressValue(ProgressUnit.Seconds, 13_500.0))
        assertEquals("0:05", formatProgressValue(ProgressUnit.Seconds, 300.0))
    }

    @Test
    fun `summaries give the share and the place`() {
        assertEquals("No progress recorded yet.", progressSummary(entry(null, null)))
        assertEquals("62% · Page 210 of 340", progressSummary(entry(ProgressUnit.Page, 210.0, 61.8, 340)))
        assertEquals("Page 210", progressSummary(entry(ProgressUnit.Page, 210.0)))
        assertEquals("40%", progressSummary(entry(ProgressUnit.Percent, 40.0, 40.0)))
        assertEquals("25% · 1 h 0 m of 4 h 0 m", progressSummary(entry(ProgressUnit.Seconds, 3600.0, 25.0, 14_400)))
        assertEquals("40", progressSummary(entry(ProgressUnit.Percent, 40.0)))
    }
}
