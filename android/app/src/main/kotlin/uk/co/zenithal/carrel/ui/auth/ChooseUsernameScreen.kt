package uk.co.zenithal.carrel.ui.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.BuildConfig
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.PROFILE_PATH
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.data.SaveProfileRequest
import uk.co.zenithal.carrel.data.UsernameAvailability
import uk.co.zenithal.carrel.data.VALID_USERNAME
import uk.co.zenithal.carrel.ui.components.CheckRow
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Shown after sign-in until the reader has chosen their public username, as on the website. */
@Composable
fun ChooseUsernameScreen() {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(true) }
    var explained by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val trimmed = username.trim()
    val status = usernameStatus(trimmed, "3 to 20 letters, numbers, underscores, or hyphens. You can change it later.")

    fun submit() {
        if (!status.canSave || saving) return
        saving = true
        saveError = null
        scope.launch {
            try {
                val profile = container.api.send(
                    HttpMethod.Put, PROFILE_PATH, SaveProfileRequest(trimmed, isPublic),
                    SaveProfileRequest.serializer(), Profile.serializer(),
                )
                // The saved profile now has a username, which moves the app on.
                container.store.save(PROFILE_PATH, profile, Profile.serializer())
            } catch (e: ApiException) {
                saveError = e.message
            } finally {
                saving = false
            }
        }
    }

    AuthCard("Choose a username.") {
        Muted("It’s how other readers will see you. Your email address stays private.")
        Column(Modifier.padding(top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field(
                "Username",
                username,
                { if (it.length <= 20) username = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                onDone = ::submit,
            )
            FormMessage(saveError ?: status.text, if (saveError != null) Tone.Error else status.tone)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CheckRow("Public profile", isPublic, { isPublic = it })
                    HelpButton(expanded = explained) { explained = !explained }
                }
                if (explained) {
                    Text(
                        "Anyone can see your shelves, ratings, reading progress, and reading dates at " +
                            "${BuildConfig.WEBSITE_URL.removePrefix("https://")}/@${trimmed.ifEmpty { "username" }}. " +
                            "You can change this in Settings.",
                        style = Carrel.type.mono,
                        color = Carrel.colors.inkFaint,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            PrimaryButton(if (saving) "Saving…" else "Continue", ::submit, enabled = status.canSave && !saving, modifier = Modifier.padding(top = 10.dp))
        }
        Gap(16)
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Muted("Not you?")
            LinkButton("Sign out", { scope.launch { container.supabase.auth.signOut() } }, color = Carrel.colors.ink)
        }
    }
}

/** The ? that opens the public profile note. */
@Composable
private fun HelpButton(expanded: Boolean, onClick: () -> Unit) {
    val color = if (expanded) Carrel.colors.accent else Carrel.colors.inkSoft
    Box(
        Modifier
            .size(22.dp)
            .border(1.dp, if (expanded) Carrel.colors.accent else Carrel.colors.ruleStrong, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "What’s on a public profile?" },
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = Carrel.type.mono, color = color)
    }
}

data class UsernameStatus(val text: String, val tone: Tone, val canSave: Boolean)

/**
 * Validates a username as it's typed, and checks it's free once typing pauses rather than on every keystroke.
 * `hint` shows while the box is empty; the reader's `current` username counts as unchanged rather than available.
 */
@Composable
fun usernameStatus(trimmed: String, hint: String, current: String? = null): UsernameStatus {
    val api = LocalContainer.current.api
    // The name last checked, and what the API said about it (null if the check failed).
    var checked by remember { mutableStateOf<Pair<String, UsernameAvailability?>?>(null) }
    LaunchedEffect(trimmed) {
        if (!VALID_USERNAME.matches(trimmed) || trimmed == current) return@LaunchedEffect
        delay(350)
        checked = trimmed to try {
            api.get("/profile/username-available?username=${trimmed.encodeURLParameter()}", UsernameAvailability.serializer())
        } catch (e: ApiException) {
            null
        }
    }
    return when {
        trimmed.isEmpty() -> UsernameStatus(hint, Tone.Neutral, false)
        trimmed == current -> UsernameStatus("That’s your current username.", Tone.Neutral, false)
        !VALID_USERNAME.matches(trimmed) -> UsernameStatus("Use 3 to 20 letters, numbers, underscores, or hyphens.", Tone.Error, false)
        checked?.first != trimmed -> UsernameStatus("Checking…", Tone.Neutral, false)
        checked?.second?.available == true -> UsernameStatus("$trimmed is available.", Tone.Ok, true)
        else -> UsernameStatus(checked?.second?.reason ?: "Couldn’t check that name. Try again.", Tone.Error, false)
    }
}
