package uk.co.zenithal.carrel.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportsTest {
    @Test
    fun splitsGoodreadsSeries() {
        assertEquals(ExportedTitle("Going to Ground", "The Shapeshifter", "3"), splitSeries("Going to Ground (The Shapeshifter, #3)"))
        assertEquals(ExportedTitle("Edgedancer", "The Stormlight Archive", "2.5"), splitSeries("Edgedancer (The Stormlight Archive #2.5)"))
    }

    @Test
    fun leavesOtherTitlesWhole() {
        assertEquals(ExportedTitle("Piranesi", null, null), splitSeries("Piranesi"))
        assertEquals(ExportedTitle("Dune (Deluxe Edition)", null, null), splitSeries("Dune (Deluxe Edition)"))
    }

    @Test
    fun searchesWithoutBrackets() {
        assertEquals("Going to Ground Ali Sparkes", pickerQuery("Going to Ground (The Shapeshifter, #3)", listOf("Ali Sparkes", "Someone")))
        assertEquals("Piranesi", pickerQuery("Piranesi [Signed]", emptyList()))
    }

    @Test
    fun readsSnakeCaseStatus() {
        val status = CarrelJson.decodeFromString(
            ImportStatus.serializer(),
            """{"id":1,"source":"story_graph","state":"waiting","overwriteExisting":false,"total":10,"matched":4,"toCheck":0,"notFound":0,"remaining":6}""",
        )
        assertEquals(ImportSource.StoryGraph, status.source)
        assertEquals(true, status.active)
    }
}
