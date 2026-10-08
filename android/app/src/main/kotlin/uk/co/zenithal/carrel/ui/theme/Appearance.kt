package uk.co.zenithal.carrel.ui.theme

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark paper, or Auto, following the phone's own setting (as the website's theme switch). */
enum class ThemeChoice(val label: String) { Auto("Auto"), Light("Light"), Dark("Dark") }

/**
 * The reader's theme and accent, chosen in Settings > Appearance. Kept on this device, as the website keeps them in the
 * browser, so they apply signed in or not.
 */
class Appearance(private val prefs: SharedPreferences) {
    private val themeState = MutableStateFlow(read(THEME, ThemeChoice.entries, ThemeChoice.Auto))
    private val accentState = MutableStateFlow(read(ACCENT, Accent.entries, Accent.Red))
    val theme: StateFlow<ThemeChoice> = themeState.asStateFlow()
    val accent: StateFlow<Accent> = accentState.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        prefs.edit { putString(THEME, choice.name) }
        themeState.value = choice
    }

    fun setAccent(accent: Accent) {
        prefs.edit { putString(ACCENT, accent.name) }
        accentState.value = accent
    }

    private fun <T : Enum<T>> read(key: String, entries: List<T>, default: T): T =
        prefs.getString(key, null)?.let { saved -> entries.firstOrNull { it.name == saved } } ?: default

    private companion object {
        const val THEME = "theme"
        const val ACCENT = "accent"
    }
}
