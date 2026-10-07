package uk.co.zenithal.carrel.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The website's colour tokens (web/src/styles.css): paper and ink, one accent. Keep the two in step. */
@Immutable
data class CarrelColors(
    val paper: Color,
    val paperRaised: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val rule: Color,
    val ruleStrong: Color,
    val accent: Color,
    val onAccent: Color,
    /** Destructive actions; fixed, so it stays a warning whichever accent is chosen. */
    val danger: Color,
    val isDark: Boolean,
)

/** Accents chosen in Settings > Appearance, each with a light-theme and a dark-theme value. Red is the default. */
enum class Accent(val light: Color, val dark: Color) {
    Red(Color(0xFF8A2B1D), Color(0xFFE07A5F)),
    Green(Color(0xFF2F5A41), Color(0xFF86BB95)),
    Blue(Color(0xFF2B3F6B), Color(0xFF93A9DC)),
    Gold(Color(0xFF8F5F0C), Color(0xFFDDAE55)),
}

fun lightColors(accent: Accent) = CarrelColors(
    paper = Color(0xFFF3EDE1),
    paperRaised = Color(0xFFFBF8F1),
    ink = Color(0xFF1D1A16),
    inkSoft = Color(0xFF5B544A),
    inkFaint = Color(0xFF8C8478),
    rule = Color(0xFFDDD3C1),
    ruleStrong = Color(0xFFB9AB92),
    accent = accent.light,
    onAccent = Color(0xFFFBF8F1),
    danger = Color(0xFFA8231A),
    isDark = false,
)

fun darkColors(accent: Accent) = CarrelColors(
    paper = Color(0xFF16120E),
    paperRaised = Color(0xFF201A14),
    ink = Color(0xFFECE4D4),
    inkSoft = Color(0xFFB9AD99),
    inkFaint = Color(0xFF85796A),
    rule = Color(0xFF342B22),
    ruleStrong = Color(0xFF54483B),
    accent = accent.dark,
    onAccent = Color(0xFF16120E),
    danger = Color(0xFFEF6B5E),
    isDark = true,
)
