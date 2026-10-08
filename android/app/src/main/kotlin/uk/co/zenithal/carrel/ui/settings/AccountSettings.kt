package uk.co.zenithal.carrel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.authMessage
import uk.co.zenithal.carrel.auth.passwordProblem
import uk.co.zenithal.carrel.data.ApiException
import uk.co.zenithal.carrel.data.LIBRARY_PATH
import uk.co.zenithal.carrel.data.LibrarySerializer
import uk.co.zenithal.carrel.data.PROFILE_PATH
import uk.co.zenithal.carrel.data.PasswordConfirmation
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.data.SaveProfileRequest
import uk.co.zenithal.carrel.ui.auth.usernameStatus
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * The reader's account, as the website's: their picture, then one row per detail (label, current value, and a link
 * that opens a small form in place), whether their profile is public, and, set apart, deleting their data.
 */
@Composable
fun AccountSettings(profile: Profile, userId: String, email: String?, onAccountDeleted: () -> Unit) {
    SettingsPage(SettingsSection.Account) {
        PictureSetting(profile)
        Gap(28)
        Rows {
            UsernameRow(profile.username.orEmpty())
            EmailRow(email)
            PasswordRow(email.orEmpty())
        }
        Gap(40)
        SectionTitle("Privacy")
        Rows(top = false) { ProfileRow(profile) }
        // Set apart, so nobody reaches them while changing their details.
        Gap(40)
        SectionTitle("Danger zone", color = Carrel.colors.danger)
        Rows(top = false) {
            DeleteLibraryRow()
            DeleteAccountRow(userId, onAccountDeleted)
        }
    }
}

/** Rows divided by rules, as the website's .setting-rows. */
@Composable
private fun Rows(top: Boolean = true, content: @Composable ColumnScope.() -> Unit) = Column {
    if (top) HorizontalDivider(color = Carrel.colors.rule)
    content()
}

/** A detail's label, its value with a link to change it, and a note under it; or, while editing, its form. */
@Composable
private fun SettingRow(
    label: String,
    value: String,
    editing: Boolean,
    onEdit: () -> Unit,
    hint: String? = null,
    action: String = "Change",
    danger: Boolean = false,
    form: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = Carrel.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(label.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em), color = colors.inkSoft)
        if (editing) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = form)
        } else {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(value, style = Carrel.type.body, color = if (danger) colors.inkSoft else colors.ink, modifier = Modifier.weight(1f))
                LinkButton(action, onEdit, color = if (danger) colors.danger else colors.ink)
            }
            hint?.let { Text(it, style = Carrel.type.body, color = colors.inkFaint, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
    HorizontalDivider(color = if (danger) colors.danger.copy(alpha = 0.5f) else colors.rule)
}

/** A form's Save (or whatever it does) and Cancel. */
@Composable
private fun FormActions(label: String, busyLabel: String, saving: Saving, onSubmit: () -> Unit, onCancel: () -> Unit, enabled: Boolean = true) {
    saving.error?.let { FormMessage(it, Tone.Error) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        PrimaryButton(if (saving.busy) busyLabel else label, onSubmit, enabled = enabled && !saving.busy)
        LinkButton("Cancel", onCancel)
    }
}

/** A change being sent: whether it's under way, and why it failed. */
class Saving(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** Runs `block` unless something is already under way. `unavailable` replaces the API's message for a 503. */
    fun run(unavailable: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                error = if (e.status == 503 && unavailable != null) unavailable else e.message
            } catch (e: Exception) {
                error = authMessage(e)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberSaving(): Saving {
    val scope = rememberCoroutineScope()
    return remember { Saving(scope) }
}

@Composable
private fun UsernameRow(current: String) {
    val container = LocalContainer.current
    val saving = rememberSaving()
    var editing by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    val trimmed = username.trim()
    val status = usernameStatus(trimmed, "3 to 20 letters, numbers, underscores, or hyphens.", current)

    fun submit() {
        if (!status.canSave) return
        saving.run {
            val profile = container.api.send(
                HttpMethod.Put, PROFILE_PATH, SaveProfileRequest(trimmed), SaveProfileRequest.serializer(), Profile.serializer(),
            )
            container.store.save(PROFILE_PATH, profile, Profile.serializer())
            saved = true
            editing = false
        }
    }

    SettingRow(
        "Username",
        current,
        editing,
        onEdit = {
            username = current
            saved = false
            saving.error = null
            editing = true
        },
        hint = if (saved) "Username saved." else null,
    ) {
        Field(
            "New username",
            username,
            {
                if (it.length <= 20) username = it
                saving.error = null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onDone = ::submit,
        )
        if (saving.error == null) FormMessage(status.text, status.tone)
        FormActions("Save", "Saving…", saving, ::submit, { editing = false }, enabled = status.canSave)
    }
}

@Composable
private fun EmailRow(current: String?) {
    val supabase = LocalContainer.current.supabase
    val saving = rememberSaving()
    var editing by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    // An address waiting for its confirmation links, from before or from this change.
    var pending by rememberSaveable { mutableStateOf(supabase.auth.currentUserOrNull()?.newEmail) }

    fun submit() = saving.run {
        val user = supabase.auth.updateUser { email = address.trim() }
        pending = user.newEmail
        sent = true
        editing = false
    }

    SettingRow(
        "Email",
        current.orEmpty(),
        editing,
        onEdit = {
            address = ""
            sent = false
            saving.error = null
            editing = true
        },
        hint = when {
            sent -> "We’ve sent a confirmation link to both addresses (check spam folders too). Your email changes once you’ve confirmed both."
            pending != null -> "Waiting for you to confirm the change to $pending."
            else -> null
        },
    ) {
        Field(
            "New email",
            address,
            { address = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onDone = ::submit,
        )
        if (saving.error == null) FormMessage("We’ll email both addresses to confirm the change.")
        FormActions("Change email", "Sending…", saving, ::submit, { editing = false }, enabled = address.isNotBlank())
    }
}

@Composable
private fun PasswordRow(email: String) {
    val supabase = LocalContainer.current.supabase
    val saving = rememberSaving()
    var editing by rememberSaveable { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var changed by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        val problem = passwordProblem(new) ?: if (new != repeated) "The new passwords don’t match." else null
        if (problem != null) {
            saving.error = problem
            return
        }
        saving.run {
            // Supabase doesn't ask for the current password, so check it here before changing anything.
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    password = current
                }
            } catch (e: AuthRestException) {
                if (e.errorCode == AuthErrorCode.InvalidCredentials) throw ApiException(400, "Your current password isn’t right.")
                throw e
            }
            supabase.auth.updateUser { password = new }
            changed = true
            editing = false
        }
    }

    SettingRow(
        "Password",
        "••••••••",
        editing,
        onEdit = {
            current = ""
            new = ""
            repeated = ""
            changed = false
            saving.error = null
            editing = true
        },
        hint = if (changed) "Password changed." else null,
    ) {
        val passwordKeys = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next)
        Field("Current password", current, { current = it }, password = true, keyboardOptions = passwordKeys)
        Field("New password (at least 8 characters)", new, { new = it }, password = true, keyboardOptions = passwordKeys)
        Field(
            "Repeat new password",
            repeated,
            { repeated = it },
            password = true,
            keyboardOptions = passwordKeys.copy(imeAction = ImeAction.Done),
            onDone = ::submit,
        )
        FormActions("Change password", "Saving…", saving, ::submit, { editing = false }, enabled = current.isNotEmpty() && new.isNotEmpty())
    }
}

/** Public or private, switched straight away: there's nothing to type. */
@Composable
private fun ProfileRow(profile: Profile) {
    val container = LocalContainer.current
    val saving = rememberSaving()
    SettingRow(
        "Profile",
        if (profile.isPublic) "Public: anyone can see your profile and shelves." else "Private: only you can see your profile and shelves.",
        editing = false,
        onEdit = {
            saving.run {
                val saved = container.api.send(
                    HttpMethod.Put, PROFILE_PATH, SaveProfileRequest(profile.username.orEmpty(), !profile.isPublic),
                    SaveProfileRequest.serializer(), Profile.serializer(),
                )
                container.store.save(PROFILE_PATH, saved, Profile.serializer())
            }
        },
        hint = saving.error,
        action = when {
            saving.busy -> "Saving…"
            profile.isPublic -> "Make private"
            else -> "Make public"
        },
    )
}

@Composable
private fun DeleteLibraryRow() {
    val container = LocalContainer.current
    val saving = rememberSaving()
    var editing by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var deleted by rememberSaveable { mutableStateOf(false) }

    fun submit() = saving.run(unavailable = "Deleting book data is unavailable right now. Try again later.") {
        container.api.send(HttpMethod.Post, "/account/delete-library", PasswordConfirmation(password), PasswordConfirmation.serializer())
        // Shelves, suggestions, the year's books, and imports were all built from the library.
        container.store.markStale()
        container.store.save(LIBRARY_PATH, emptyList(), LibrarySerializer)
        deleted = true
        editing = false
    }

    SettingRow(
        "Delete book data",
        "Empty your library but keep your account.",
        editing,
        onEdit = {
            password = ""
            deleted = false
            saving.error = null
            editing = true
        },
        hint = if (deleted) "Your book data has been deleted." else null,
        action = "Delete",
        danger = true,
    ) {
        Warning(
            "This deletes every book in your library, along with your ratings, progress, reading history, and imports. " +
                "Your account and username stay. It can’t be undone.",
        )
        PasswordField(password, { password = it }, ::submit)
        DangerActions("Delete my book data", saving, ::submit, enabled = password.isNotEmpty()) { editing = false }
    }
}

@Composable
private fun DeleteAccountRow(userId: String, onDeleted: () -> Unit) {
    val container = LocalContainer.current
    val saving = rememberSaving()
    var editing by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    fun submit() = saving.run(unavailable = "Account deletion is unavailable right now. Try again later.") {
        container.api.send(HttpMethod.Post, "/account/delete", PasswordConfirmation(password), PasswordConfirmation.serializer())
        // Leave nothing of theirs on this phone, and don't leave the next reader on these settings.
        container.recentSearches.clear(userId)
        // Signing out closes this page, which would otherwise stop the rest part-way.
        withContext(NonCancellable) {
            onDeleted()
            // The account no longer exists, so only this phone's session is cleared.
            container.supabase.auth.signOut(SignOutScope.LOCAL)
        }
    }

    SettingRow(
        "Delete account",
        "Permanently delete your account.",
        editing,
        onEdit = {
            password = ""
            saving.error = null
            editing = true
        },
        action = "Delete",
        danger = true,
    ) {
        Warning("This deletes your account, along with your library, ratings, progress, and reading history. It can’t be undone.")
        PasswordField(password, { password = it }, ::submit)
        DangerActions("Delete my account", saving, ::submit, enabled = password.isNotEmpty()) { editing = false }
    }
}

@Composable
private fun Warning(text: String) = Text(text, style = Carrel.type.body, color = Carrel.colors.inkSoft)

@Composable
private fun PasswordField(password: String, onChange: (String) -> Unit, onDone: () -> Unit) = Field(
    "Your password",
    password,
    onChange,
    password = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    onDone = onDone,
)

/** The delete button, in the danger colour rather than the accent, and Cancel. */
@Composable
private fun DangerActions(label: String, saving: Saving, onSubmit: () -> Unit, enabled: Boolean, onCancel: () -> Unit) {
    val colors = Carrel.colors
    saving.error?.let { FormMessage(it, Tone.Error) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        PrimaryButton(if (saving.busy) "Deleting…" else label, onSubmit, enabled = enabled && !saving.busy, color = colors.danger)
        LinkButton("Cancel", onCancel)
    }
}
