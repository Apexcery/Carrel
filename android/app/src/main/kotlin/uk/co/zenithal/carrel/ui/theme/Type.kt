package uk.co.zenithal.carrel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uk.co.zenithal.carrel.R

/**
 * The website's type scale (web/src/styles.css), at phone sizes: use these instead of one-off sizes. The display sizes
 * are what the website's clamp() gives on a phone.
 */
@Immutable
data class CarrelType(
    /** All mono text: labels, kickers, buttons, dates, lengths, ISBNs, ratings, small links. */
    val mono: TextStyle,
    /** Mono at weight 500: kickers, section titles, and buttons. */
    val monoMedium: TextStyle,
    /** Serif: all running text, subtitles, bylines, form fields, card text. */
    val body: TextStyle,
    /** Display: titles in lists. */
    val heading: TextStyle,
    /** Display: search and series page titles. */
    val displayMedium: TextStyle,
    /** Display: book and home titles. */
    val displayLarge: TextStyle,
    /** The sign-in card's title (2.6rem on the website). */
    val signInTitle: TextStyle,
)

private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/**
 * Literata at a size: its optical size axis matched to the size, as the website's font-optical-sizing: auto does
 * (finer cuts for large text), at the given weight. Dark mode reads lighter, so body text there is 360, not 400.
 */
private fun literata(size: Float, weight: Int) = FontFamily(
    Font(R.font.literata, FontWeight(weight), FontStyle.Normal, variationSettings = settings(size, weight)),
    Font(R.font.literata_italic, FontWeight(weight), FontStyle.Italic, variationSettings = settings(size, weight)),
)

private fun settings(size: Float, weight: Int) =
    FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", size.coerceIn(7f, 72f)))

private fun serif(size: Float, weight: Int, lineHeight: Float) =
    TextStyle(fontFamily = literata(size, weight), fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = (size * lineHeight).sp)

private val MONO_SIZE: TextUnit = 12.8.sp

fun carrelType(isDark: Boolean): CarrelType {
    val bodyWeight = if (isDark) 360 else 400
    return CarrelType(
        mono = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = MONO_SIZE, lineHeight = 1.5.em),
        monoMedium = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = MONO_SIZE, lineHeight = 1.5.em),
        body = serif(16f, bodyWeight, 1.55f),
        heading = serif(22.4f, 400, 1.2f),
        displayMedium = serif(35.2f, 400, 1.05f),
        displayLarge = serif(38.4f, 400, 1.02f),
        signInTitle = serif(41.6f, 400, 1.05f),
    )
}
