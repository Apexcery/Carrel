@file:OptIn(ExperimentalReadiumApi::class)

package uk.co.zenithal.carrel.reader

import android.content.SharedPreferences
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import uk.co.zenithal.carrel.ui.theme.Accent
import uk.co.zenithal.carrel.ui.theme.CarrelColors
import uk.co.zenithal.carrel.ui.theme.blackColors
import uk.co.zenithal.carrel.ui.theme.darkColors
import uk.co.zenithal.carrel.ui.theme.lightColors
import uk.co.zenithal.carrel.ui.theme.nightColors
import uk.co.zenithal.carrel.ui.theme.sepiaColors
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/** How the reader shows a book, kept on the phone; the website's eReader has the same settings (reader/look.ts). */
internal data class ReaderLook(
    /** 1 is the book's own size. */
    val fontSize: Double,
    val pages: Pages,
    val theme: ReaderTheme,
    val font: ReaderFont,
    val spacing: Step,
    val marginTop: Step,
    val marginBottom: Step,
    val marginLeft: Step,
    val marginRight: Step,
) {
    /** Whether changing from `other` flows the text again; colours don't, and pages side by side reopen the reader. */
    fun reflowsFrom(other: ReaderLook) =
        fontSize != other.fontSize || font != other.font || spacing != other.spacing || marginTop != other.marginTop ||
            marginBottom != other.marginBottom || marginLeft != other.marginLeft || marginRight != other.marginRight
}

/** Each margin: its name, and how to read it from and change it in a look. */
internal enum class Margin(val label: String, val of: (ReaderLook) -> Step, val set: (ReaderLook, Step) -> ReaderLook) {
    Top("Top", { it.marginTop }, { look, step -> look.copy(marginTop = step) }),
    Bottom("Bottom", { it.marginBottom }, { look, step -> look.copy(marginBottom = step) }),
    Left("Left", { it.marginLeft }, { look, step -> look.copy(marginLeft = step) }),
    Right("Right", { it.marginRight }, { look, step -> look.copy(marginRight = step) }),
}

/** Pages side by side: automatically when the screen is wide (unfolded, a tablet, or landscape), or always one or two. */
internal enum class Pages(val label: String) {
    Auto("Auto"), One("One page"), Two("Two pages");

    fun columns(wide: Boolean) = when (this) {
        Auto -> if (wide) ColumnCount.TWO else ColumnCount.ONE
        One -> ColumnCount.ONE
        Two -> ColumnCount.TWO
    }
}

/** The page's colours: Carrel's own paper and ink (light or dark, as the app is), or one of the reader's own. */
internal enum class ReaderTheme(val label: String) {
    Carrel("Carrel"), Sepia("Sepia"), Night("Night"), Black("Black");

    fun colors(dark: Boolean, accent: Accent) = when (this) {
        Carrel -> if (dark) darkColors(accent) else lightColors(accent)
        Sepia -> sepiaColors(accent)
        Night -> nightColors(accent)
        Black -> blackColors(accent)
    }
}

/** Each font, and the family Readium shows it in (null keeps the book's own). */
internal enum class ReaderFont(val label: String, val family: FontFamily?) {
    Book("Book’s own", null),
    Literata("Literata", LITERATA),
    Serif("Serif", FontFamily.SERIF),
    Sans("Sans", FontFamily.SANS_SERIF),
    Atkinson("Atkinson Hyperlegible", ATKINSON_HYPERLEGIBLE),
    OpenDyslexic("OpenDyslexic", OPEN_DYSLEXIC),
}

/**
 * Line spacing or a margin: less than usual, usual (for line spacing, the book's own), or more. The labels differ, so
 * each has its own. The margins are space around the book, outside Readium's pages: above and below, and beside.
 */
internal enum class Step(val spacingLabel: String, val marginLabel: String, val lineHeight: Double?, val aboveBelow: Dp, val beside: Dp) {
    Less("Tight", "Narrow", 1.3, 16.dp, 0.dp),
    Normal("Normal", "Normal", null, 40.dp, 10.dp),
    More("Loose", "Wide", 1.8, 72.dp, 40.dp),
}

internal val SharedPreferences.look
    get() = ReaderLook(
        fontSize = getFloat(FONT_SIZE, 1f).toDouble(),
        pages = enumOf(PAGES, Pages.Auto),
        theme = enumOf(THEME, ReaderTheme.Carrel),
        font = enumOf(FONT, ReaderFont.Book),
        spacing = enumOf(SPACING, Step.Normal),
        marginTop = enumOf(MARGIN_TOP, Step.Normal),
        marginBottom = enumOf(MARGIN_BOTTOM, Step.Normal),
        marginLeft = enumOf(MARGIN_LEFT, Step.Normal),
        marginRight = enumOf(MARGIN_RIGHT, Step.Normal),
    )

internal fun SharedPreferences.saveLook(look: ReaderLook) = edit {
    putFloat(FONT_SIZE, look.fontSize.toFloat())
    putString(PAGES, look.pages.name)
    putString(THEME, look.theme.name)
    putString(FONT, look.font.name)
    putString(SPACING, look.spacing.name)
    putString(MARGIN_TOP, look.marginTop.name)
    putString(MARGIN_BOTTOM, look.marginBottom.name)
    putString(MARGIN_LEFT, look.marginLeft.name)
    putString(MARGIN_RIGHT, look.marginRight.name)
}

private inline fun <reified T : Enum<T>> SharedPreferences.enumOf(key: String, default: T) =
    enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: default

/**
 * The book in the look's colours, font, and spacing, at its text size, in one or two columns. Readium only applies a
 * line height with the publisher's styles off, so they're only off while the spacing is changed. Readium's own margin
 * either side of each page stays small, so a Narrow margin can be narrower than its usual one; Normal makes up the
 * difference.
 */
internal fun readerPreferences(look: ReaderLook, colors: CarrelColors, columns: ColumnCount) = EpubPreferences(
    backgroundColor = ReadiumColor(colors.paper.toArgb()),
    columnCount = columns,
    fontFamily = look.font.family,
    fontSize = look.fontSize,
    lineHeight = look.spacing.lineHeight,
    pageMargins = PAGE_MARGINS,
    publisherStyles = if (look.spacing.lineHeight != null) false else null,
    textColor = ReadiumColor(colors.ink.toArgb()),
)

/**
 * The reader's own fonts, served to the book from the app's assets (the book's web view can't reach its resources).
 * OpenDyslexic has a name of its own, since Readium declares its own OpenDyslexic, without bold or italic.
 */
internal fun EpubNavigatorFragment.Configuration.addReaderFonts() {
    servedAssets = servedAssets + "fonts/.*"
    addFontFamilyDeclaration(LITERATA) {
        addFontFace {
            addSource("fonts/literata.ttf")
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/literata_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }
    addFourFaces(ATKINSON_HYPERLEGIBLE, "atkinson_hyperlegible", "ttf")
    addFourFaces(OPEN_DYSLEXIC, "opendyslexic", "woff2")
}

/** A family in regular, italic, bold, and bold italic, from `fonts/<name>[_bold][_italic].<extension>`. */
private fun EpubNavigatorFragment.Configuration.addFourFaces(family: FontFamily, name: String, extension: String) {
    addFontFamilyDeclaration(family) {
        for (weight in listOf(FontWeight.NORMAL, FontWeight.BOLD)) {
            for (style in listOf(FontStyle.NORMAL, FontStyle.ITALIC)) {
                addFontFace {
                    val suffix = (if (weight == FontWeight.BOLD) "_bold" else "") + (if (style == FontStyle.ITALIC) "_italic" else "")
                    addSource("fonts/$name$suffix.$extension")
                    setFontStyle(style)
                    setFontWeight(weight)
                }
            }
        }
    }
}

private val LITERATA = FontFamily("Literata")
private val ATKINSON_HYPERLEGIBLE = FontFamily("Atkinson Hyperlegible")
private val OPEN_DYSLEXIC = FontFamily("Carrel OpenDyslexic")

/** Readium's margin either side of each page, as a share of its usual one. */
private const val PAGE_MARGINS = 0.5

internal const val FONT_STEP = 0.1
internal const val MIN_FONT_SIZE = 0.7
internal const val MAX_FONT_SIZE = 2.0

private const val FONT_SIZE = "fontSize"
internal const val PAGES = "pages"
private const val THEME = "theme"
private const val FONT = "font"
private const val SPACING = "spacing"
private const val MARGIN_TOP = "marginTop"
private const val MARGIN_BOTTOM = "marginBottom"
private const val MARGIN_LEFT = "marginLeft"
private const val MARGIN_RIGHT = "marginRight"
