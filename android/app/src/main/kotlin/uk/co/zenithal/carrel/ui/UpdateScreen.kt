package uk.co.zenithal.carrel.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.zenithal.carrel.BuildConfig
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.AppUpdate
import uk.co.zenithal.carrel.data.InstallState
import uk.co.zenithal.carrel.ui.auth.Muted
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.Carrel
import java.util.Locale

/**
 * A newer version of the app: what's changed, and whether to update now, at the next launch, or skip this version.
 * It's the sign-in card's look, but the changes scroll inside it, so the buttons along its bottom stay in view however
 * many there are.
 */
@Composable
fun UpdateScreen(update: AppUpdate) {
    val updates = LocalContainer.current.updates
    val context = LocalContext.current
    val install by updates.install.collectAsStateWithLifecycle()
    val downloading = install is InstallState.Downloading
    val colors = Carrel.colors
    val scroll = rememberScrollState()
    // Back is "Not now", except while downloading. While installing it stays, in case Android never asks to confirm
    // (e.g. if the reader switched away before it could).
    BackHandler(enabled = !downloading) { updates.notNow() }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(6.dp, clip = false)
                .background(colors.paperRaised),
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(colors.accent))
            // Only as tall as it needs to be, up to the space left over by the buttons.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll)
                    .padding(start = 28.dp, end = 28.dp, top = 33.dp, bottom = 24.dp),
            ) {
                Kicker("Carrel")
                Text(
                    "Carrel ${update.versionName} is out.",
                    style = Carrel.type.signInTitle,
                    color = colors.ink,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                )
                Muted("You have ${BuildConfig.VERSION_NAME}.${update.size.takeIf { it > 0 }?.let { " The update is ${megabytes(it)}." }.orEmpty()}")
                if (update.changes.isNotEmpty()) {
                    SectionTitle("What’s new", Modifier.padding(top = 24.dp, bottom = 12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        update.changes.forEach { change ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", style = Carrel.type.body, color = colors.accent)
                                Text(change, style = Carrel.type.body, color = colors.ink)
                            }
                        }
                    }
                }
                Muted(
                    "Google Play Protect may offer to scan the update first, as Carrel isn’t from the Play Store. Scanning it or " +
                        "installing without scanning both update Carrel.",
                    Modifier.padding(top = 24.dp),
                )
            }
            // A rule marks where the changes scroll under the buttons.
            if (scroll.maxValue > 0) HorizontalDivider(color = colors.rule)
            Column(
                Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val state = install) {
                    is InstallState.Downloading -> FormMessage("Downloading… ${(state.fraction * 100).toInt()}%")
                    InstallState.Installing -> FormMessage("Installing. Carrel will close and reopen.")
                    is InstallState.Failed -> FormMessage(state.message, Tone.Error)
                    InstallState.Idle -> Unit
                }
                if (!downloading) {
                    if (install != InstallState.Installing) {
                        PrimaryButton("Update", {
                            // Allowing Carrel to install apps closes it, so that comes first, from Settings: asked
                            // partway through the install, Android would close Carrel and not reopen it afterwards.
                            if (context.packageManager.canRequestPackageInstalls()) updates.install(update)
                            else context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)))
                        })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinkButton("Not now", updates::notNow, color = colors.ink)
                        LinkButton("Skip this version", { updates.skip(update) })
                    }
                }
            }
        }
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.UK, "%.1f MB", bytes / 1_000_000.0)
