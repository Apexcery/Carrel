package uk.co.zenithal.carrel.reader

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.roundToInt

/**
 * What the reader's footers show, and how fast the reader reads, kept on the phone apart from the look (changing them
 * doesn't lay the book out again); the website's eReader has the same settings (reader/footer.ts).
 */
internal enum class FooterItem(val label: String, val default: FooterSetting) {
    Chapter("Chapter", FooterSetting(FooterSlot.Left, controls = true, always = true)),
    Pages("Chapter pages", FooterSetting(FooterSlot.Left, controls = true, always = true)),
    Percent("Percentage read", FooterSetting(FooterSlot.Right, controls = true, always = false)),
    PagesLeft("Pages left in chapter", FooterSetting(FooterSlot.Left, controls = false, always = false)),
    ChapterTime("Time left in chapter", FooterSetting(FooterSlot.Left, controls = false, always = false)),
    BookTime("Time left in book", FooterSetting(FooterSlot.Right, controls = false, always = false)),
    Clock("Current time",FooterSetting(FooterSlot.Centre, controls = true, always = true)),
}

internal enum class FooterSlot(val label: String) { Left("Left"), Centre("Centre"), Right("Right") }

/**
 * Where an item goes, and which footers show it: the one with the controls (under the seek bar), and the one always
 * under the page.
 */
internal data class FooterSetting(val slot: FooterSlot, val controls: Boolean, val always: Boolean)

internal val SharedPreferences.footer
    get() = FooterItem.entries.associateWith { item ->
        FooterSetting(
            slot = FooterSlot.entries.firstOrNull { it.name == getString("footer${item.name}Slot", null) } ?: item.default.slot,
            controls = getBoolean("footer${item.name}Controls", item.default.controls),
            always = getBoolean("footer${item.name}Always", item.default.always),
        )
    }

internal fun SharedPreferences.saveFooter(item: FooterItem, setting: FooterSetting) = edit {
    putString("footer${item.name}Slot", setting.slot.name)
    putBoolean("footer${item.name}Controls", setting.controls)
    putBoolean("footer${item.name}Always", setting.always)
}

/**
 * How fast the reader reads, in seconds a Readium position (about the same amount of text, however it's laid out), from
 * the time between turning one page forward and the next.
 */
internal class ReadingSpeed(private val prefs: SharedPreferences) {
    private var secondsPerPosition = prefs.getFloat(SECONDS_PER_POSITION, 0f).toDouble().takeIf { it > 0 }
    private var turns = prefs.getInt(TURNS, 0)
    private var last: Pair<Double, Long>? = null

    /** Seconds a position, once there's enough to go on. */
    val estimate get() = secondsPerPosition?.takeIf { turns >= TURNS_BEFORE_SHOWN }

    /**
     * Takes in where the reader is: `forward` when they've turned one page on from the last place, which times how long
     * it took them; anywhere else (opening the book, a jump) starts timing afresh from there.
     */
    fun arrived(progression: Double, positions: Int, forward: Boolean, at: Long = System.currentTimeMillis()) {
        val (lastProgression, lastAt) = last.also { last = progression to at } ?: return
        if (!forward) return
        val took = at - lastAt
        val read = (progression - lastProgression) * positions
        if (took < QUICKEST_TURN_MS || took > SLOWEST_TURN_MS || read <= 0) return
        val seconds = took / 1000.0 / read
        turns++
        val weight = maxOf(LEAST_WEIGHT, 1.0 / turns)
        val speed = secondsPerPosition?.let { it * (1 - weight) + seconds * weight } ?: seconds
        secondsPerPosition = speed
        prefs.edit {
            putFloat(SECONDS_PER_POSITION, speed.toFloat())
            putInt(TURNS, turns)
        }
    }

    private companion object {
        /** Page turns quicker than this are flicking through, and slower ones a pause, so neither counts towards the speed. */
        const val QUICKEST_TURN_MS = 4_000L
        const val SLOWEST_TURN_MS = 5 * 60_000L
        /** Turns timed before the speed is shown, unless it's been timed before. */
        const val TURNS_BEFORE_SHOWN = 3
        /** Each turn counts for at least this much of the speed, so it follows the reader as they settle in. */
        const val LEAST_WEIGHT = 0.1
        const val SECONDS_PER_POSITION = "speedSecondsPerPosition"
        const val TURNS = "speedTurns"
    }
}

/** The pages of the chapter after those showing, e.g. "4 pages left in chapter". */
fun pagesLeftText(place: ChapterPlace) = when (val left = place.pages - place.last) {
    0 -> "Last page in chapter"
    1 -> "1 page left in chapter"
    else -> "$left pages left in chapter"
}

/** A time left, e.g. "12 min" or "2 h 5 min", to the minute. */
fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return when {
        minutes < 1 -> "Less than a minute"
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
