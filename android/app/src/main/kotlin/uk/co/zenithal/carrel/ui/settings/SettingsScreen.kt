package uk.co.zenithal.carrel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.theme.Accent
import uk.co.zenithal.carrel.ui.theme.Carrel
import uk.co.zenithal.carrel.ui.theme.ThemeChoice
import uk.co.zenithal.carrel.ui.theme.darkColors
import uk.co.zenithal.carrel.ui.theme.lightColors

/** The sections of Settings, as the website's. Signed out, only Appearance applies. */
enum class SettingsSection(val label: String, val description: String, val membersOnly: Boolean) {
    Account("Account", "Your picture, username, email, password, and privacy.", true),
    ImportExport("Import & Export", "Bring your library over from Goodreads or StoryGraph, or save a copy.", true),
    Appearance("Appearance", "Light or dark, and your accent colour.", false),
}

/** Settings, opened from the You tab: its sections, then who's signed in and a way to sign out. */
@Composable
fun SettingsScreen(email: String?, open: (SettingsSection) -> Unit) {
    val colors = Carrel.colors
    val supabase = LocalContainer.current.supabase
    val scope = rememberCoroutineScope()
    val signedIn = email != null
    SettingsPage(title = "Settings") {
        Column {
            HorizontalDivider(color = colors.rule)
            SettingsSection.entries.filter { signedIn || !it.membersOnly }.forEach { section ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { open(section) }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.label, style = Carrel.type.heading, color = colors.ink)
                        Text(section.description, style = Carrel.type.body, color = colors.inkSoft)
                    }
                    Text("→", style = Carrel.type.mono, color = colors.inkSoft)
                }
                HorizontalDivider(color = colors.rule)
            }
        }
        if (email != null) {
            Gap(40)
            Text("Signed in as $email", style = Carrel.type.mono, color = colors.inkSoft)
            LinkButton("Sign out", { scope.launch { supabase.auth.signOut() } }, Modifier.padding(top = 4.dp), color = colors.ink)
        }
    }
}

/** The gear at the top of the You tab that opens Settings. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier) {
        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Carrel.colors.inkSoft)
    }
}

/** The theme and accent, which apply straight away and stay on this device. */
@Composable
fun AppearanceSettings() {
    val appearance = LocalContainer.current.appearance
    val theme by appearance.theme.collectAsStateWithLifecycle()
    val accent by appearance.accent.collectAsStateWithLifecycle()
    val colors = Carrel.colors
    SettingsPage(section = SettingsSection.Appearance) {
        SectionTitle("Theme")
        Note("Auto follows your phone’s light or dark setting.", Modifier.padding(top = 12.dp))
        OptionSwitch(ThemeChoice.entries.map { it to it.label }, theme, appearance::setTheme)
        Gap(40)
        SectionTitle("Accent colour")
        Note("Used for links, buttons, and highlights.", Modifier.padding(top = 12.dp))
        OptionSwitch(Accent.entries.map { it to it.name }, accent, appearance::setAccent) { option ->
            // Each swatch shows its own accent as it looks on the current paper.
            val swatch = if (colors.isDark) darkColors(option).accent else lightColors(option).accent
            Box(Modifier.size(10.dp).background(swatch, RoundedCornerShape(50)))
        }
        Gap(40)
        Note("Appearance is saved on this device.")
    }
}

/** A settings page: the website's gutter and a title under a "Settings" kicker, scrolling. */
@Composable
fun SettingsPage(section: SettingsSection, content: @Composable ColumnScope.() -> Unit) =
    SettingsPage(section.label, kicker = "Settings", content)

@Composable
private fun SettingsPage(title: String, kicker: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = Carrel.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        kicker?.let { Kicker(it) }
        Text(title, style = Carrel.type.displayLarge, color = colors.ink, modifier = Modifier.padding(top = if (kicker != null) 8.dp else 0.dp, bottom = 28.dp))
        content()
    }
}

/** The website's .settings-note: soft ink, before what it explains. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Carrel.type.body, color = Carrel.colors.inkSoft, modifier = modifier.padding(bottom = 14.dp))
}

/** The website's .option-switch: joined mono buttons in one border, the chosen one in ink. */
@Composable
private fun <T> OptionSwitch(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    val colors = Carrel.colors
    val shape = RoundedCornerShape(2.dp)
    Row(Modifier.border(1.dp, colors.ruleStrong, shape)) {
        options.forEachIndexed { index, (value, label) ->
            val chosen = value == selected
            if (index > 0) Box(Modifier.width(1.dp).height(36.dp).background(colors.ruleStrong))
            Row(
                Modifier
                    .background(if (chosen) colors.ink else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(value) }
                    .height(36.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                leading?.invoke(value)
                Text(label.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.06.em), color = if (chosen) colors.paper else colors.inkSoft)
            }
        }
    }
}
