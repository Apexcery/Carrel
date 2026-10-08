package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val request = SaveEntryRequest(
        status = ReadingStatus.WantToRead,
        editionId = null,
        rating = 4.5,
        progressUnit = ProgressUnit.Page,
        progressValue = 120.0,
        today = "2026-10-07",
    )

    private fun encode(request: SaveEntryRequest) = CarrelJson.encodeToString(SaveEntryRequest.serializer(), request)

    @Test
    fun `a save without reads leaves the reading history alone`() {
        val json = encode(request)
        assertFalse(json, "\"reads\"" in json)
        assertTrue(json, "\"status\":\"want_to_read\"" in json)
        assertTrue(json, "\"progressUnit\":\"page\"" in json)
        assertTrue(json, "\"today\":\"2026-10-07\"" in json)
    }

    @Test
    fun `removing every read sends an empty history`() {
        val json = encode(request.copy(reads = emptyList()))
        assertTrue(json, "\"reads\":[]" in json)
    }

    @Test
    fun `statuses go by their API names`() {
        assertTrue("\"did_not_finish\"" in encode(request.copy(status = ReadingStatus.DidNotFinish)))
        assertTrue("\"seconds\"" in encode(request.copy(progressUnit = ProgressUnit.Seconds)))
    }

    private val entry = LibraryEntry(
        id = 1, bookId = 7, status = ReadingStatus.Reading, rating = null, editionId = null,
        progressUnit = ProgressUnit.Page, progressValue = 50.0, progressPercent = 25.0, progressTotal = 200,
        addedAt = "2026-09-01T10:00:00Z", updatedAt = "2026-09-01T10:00:00Z",
        reads = listOf(Read(5, "2026-09-01", null, false)),
    )
    private val changedAt = "2026-10-08T12:00:00Z"

    @Test
    fun `finishing offline closes the open read today`() {
        val saved = entry.applied(entry.toRequest().copy(status = ReadingStatus.Read, today = "2026-10-08"), changedAt, 200)
        assertEquals(listOf(Read(5, "2026-09-01", "2026-10-08", false)), saved.reads)
        assertEquals(100.0, saved.progressPercent)
        assertEquals(changedAt, saved.updatedAt)
    }

    @Test
    fun `finishing without an open read adds one finished today`() {
        val wanted = entry.copy(status = ReadingStatus.WantToRead, reads = emptyList())
        val saved = wanted.applied(wanted.toRequest().copy(status = ReadingStatus.Read, today = "2026-10-08"), changedAt, 200)
        assertEquals(listOf(Read(-1, null, "2026-10-08", false)), saved.reads)
    }

    @Test
    fun `starting offline opens a read today, unless one is open`() {
        val wanted = entry.copy(status = ReadingStatus.WantToRead, reads = emptyList())
        assertEquals(listOf(Read(-1, "2026-10-08", null, false)), wanted.applied(wanted.toRequest().copy(status = ReadingStatus.Reading, today = "2026-10-08"), changedAt, 200).reads)
        val paused = entry.copy(status = ReadingStatus.Paused)
        assertEquals(entry.reads, paused.applied(paused.toRequest().copy(status = ReadingStatus.Reading), changedAt, 200).reads)
    }

    @Test
    fun `progress offline works out its percentage`() {
        assertEquals(60.0, entry.applied(entry.toRequest().copy(progressValue = 120.0), changedAt, 200).progressPercent)
        assertEquals(42.0, entry.applied(entry.toRequest().copy(progressUnit = ProgressUnit.Percent, progressValue = 42.0), changedAt, 200).progressPercent)
        assertEquals(33.33, entry.copy(progressUnit = null, progressTotal = null).applied(entry.toRequest().copy(progressValue = 100.0), changedAt, 300).progressPercent)
        assertNull(entry.applied(entry.toRequest().copy(progressUnit = ProgressUnit.Seconds, progressValue = 600.0), changedAt, 200).progressPercent)
    }

    @Test
    fun `reads added offline get made-up ids`() {
        val reads = listOf(ReadInput(5, "2026-09-01", "2026-09-20", false), ReadInput(null, "2026-10-01", null, false))
        val saved = entry.applied(entry.toRequest().copy(reads = reads), changedAt, 200)
        assertEquals(listOf(5L, -1L), saved.reads.map { it.id })
    }
}
