package uk.co.zenithal.carrel.data

import org.junit.Assert.assertFalse
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
}
