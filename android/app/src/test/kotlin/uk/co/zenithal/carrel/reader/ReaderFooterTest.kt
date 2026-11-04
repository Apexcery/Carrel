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
    fun readsSavedPlaces() {
        assertEquals(FooterPlace.Centre, savedPlace(FooterItem.Chapter, Footer.Always, "Centre", "Left", true))
        assertEquals(FooterPlace.Off, savedPlace(FooterItem.Chapter, Footer.Always, "Off", null, null))
        // Nothing saved: the defaults.
        assertEquals(FooterPlace.Right, savedPlace(FooterItem.Percent, Footer.Controls, null, null, null))
        assertEquals(FooterPlace.Off, savedPlace(FooterItem.Percent, Footer.Always, null, null, null))
    }

    @Test
    fun turnsTheOldSlotAndSwitchesIntoPlaces() {
        assertEquals(FooterPlace.Right, savedPlace(FooterItem.PagesLeft, Footer.Controls, null, "Right", true))
        assertEquals(FooterPlace.Off, savedPlace(FooterItem.Chapter, Footer.Always, null, "Left", false))
        // An unknown slot, or a switch never saved: the defaults.
        assertEquals(FooterPlace.Left, savedPlace(FooterItem.Chapter, Footer.Controls, null, "Middle", false))
        assertEquals(FooterPlace.Centre, savedPlace(FooterItem.Clock, Footer.Always, null, "Right", null))
    }

    @Test
    fun showsThePercentageWhileSeeking() {
        fun places(controls: FooterPlace, always: FooterPlace): FooterPlaces = Footer.entries.associateWith { footer ->
            FooterItem.entries.associateWith { if (it == FooterItem.Percent) (if (footer == Footer.Controls) controls else always) else FooterPlace.Off }
        }
        assertEquals(FooterPlace.Off, seekingPlaces(places(FooterPlace.Off, FooterPlace.Left), false).getValue(FooterItem.Percent))
        assertEquals(FooterPlace.Centre, seekingPlaces(places(FooterPlace.Centre, FooterPlace.Left), true).getValue(FooterItem.Percent))
        assertEquals(FooterPlace.Left, seekingPlaces(places(FooterPlace.Off, FooterPlace.Left), true).getValue(FooterItem.Percent))
        assertEquals(FooterPlace.Right, seekingPlaces(places(FooterPlace.Off, FooterPlace.Off), true).getValue(FooterItem.Percent))
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
