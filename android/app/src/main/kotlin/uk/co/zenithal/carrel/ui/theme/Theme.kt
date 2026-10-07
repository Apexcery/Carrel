package uk.co.zenithal.carrel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { lightColors(Accent.Red) }
private val LocalType = staticCompositionLocalOf { carrelType(isDark = false) }

/** Carrel's colours and type, for anything Material's own slots don't cover. */
object Carrel {
    val colors: CarrelColors @Composable get() = LocalColors.current
    val type: CarrelType @Composable get() = LocalType.current
}

/** Paper and ink in light or dark, following the phone's setting, with Material's components mapped onto them. */
@Composable
fun CarrelTheme(dark: Boolean = isSystemInDarkTheme(), accent: Accent = Accent.Red, content: @Composable () -> Unit) {
    val colors = if (dark) darkColors(accent) else lightColors(accent)
    val type = carrelType(dark)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.inkSoft,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.paper,
        onSurface = colors.ink,
        surfaceVariant = colors.paperRaised,
        onSurfaceVariant = colors.inkSoft,
        surfaceContainer = colors.paperRaised,
        surfaceContainerLow = colors.paperRaised,
        surfaceContainerHigh = colors.paperRaised,
        surfaceContainerHighest = colors.paperRaised,
        outline = colors.ruleStrong,
        outlineVariant = colors.rule,
        error = colors.danger,
    )
    val typography = Typography(
        displayLarge = type.displayLarge,
        displayMedium = type.displayMedium,
        headlineMedium = type.heading,
        titleMedium = type.body,
        bodyLarge = type.body,
        bodyMedium = type.body,
        labelLarge = type.monoMedium,
        labelMedium = type.mono,
        labelSmall = type.mono,
    )
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
