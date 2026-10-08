package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.zenithal.carrel.reader.readPercent

class PhoneBooksTest {
    @Test
    fun findsIsbnsAmongTheIdentifiers() {
        val opf = """
            <package unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">urn:uuid:12345678-1234-1234-1234-123456789012</dc:identifier>
                <dc:identifier opf:scheme="ISBN">978-0-14-143951-8</dc:identifier>
                <dc:identifier>urn:isbn:0141439513</dc:identifier>
                <dc:identifier>isbn:9780141439518</dc:identifier>
                <dc:title>Pride and Prejudice</dc:title>
              </metadata>
            </package>
        """
        assertEquals(listOf("9780141439518", "0141439513"), isbnsInPackage(opf))
    }

    @Test
    fun ignoresIdentifiersThatArentIsbns() {
        val opf = """
            <dc:identifier>urn:uuid:12345678-1234-1234-1234-123456789012</dc:identifier>
            <dc:identifier>https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice</dc:identifier>
            <dc:identifier>9780141439519</dc:identifier>
            <identifier>1234567890123</identifier>
        """
        assertEquals(emptyList<String>(), isbnsInPackage(opf))
    }

    @Test
    fun readsWholePercentages() {
        assertEquals(0, readPercent(0.0))
        assertEquals(43, readPercent(0.4321))
        assertEquals(100, readPercent(0.996))
        assertEquals(100, readPercent(1.2))
    }

    private fun save(status: ReadingStatus, today: String, progress: Double? = null, reads: List<ReadInput>? = null) =
        SaveEntryRequest(status, null, null, progress?.let { ProgressUnit.Percent }, progress, today, reads)

    @Test
    fun aLaterSaveReplacesAnEarlierOneLikeIt() {
        val earlier = save(ReadingStatus.Reading, "2026-10-08", 10.0)
        val later = save(ReadingStatus.Reading, "2026-10-09", 15.0)
        // The earlier date stays, for the read dates a status change sets.
        assertEquals(later.copy(today = "2026-10-08"), replacing(earlier, later))
    }

    @Test
    fun keepsSavesThatChangeTheStatusOrHistory() {
        assertNull(replacing(save(ReadingStatus.WantToRead, "2026-10-08"), save(ReadingStatus.Reading, "2026-10-08", 5.0)))
        val history = listOf(ReadInput(null, "2026-10-01", null, false))
        assertNull(replacing(save(ReadingStatus.Reading, "2026-10-08", reads = history), save(ReadingStatus.Reading, "2026-10-08", 5.0)))
        assertNull(replacing(save(ReadingStatus.Reading, "2026-10-08", 5.0), save(ReadingStatus.Reading, "2026-10-08", reads = history)))
    }
}
