package uk.co.zenithal.carrel.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.MIN_PASSWORD_LENGTH
import uk.co.zenithal.carrel.auth.authMessage
import uk.co.zenithal.carrel.auth.passwordProblem
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone

/**
 * Choosing a new password after following a reset or invitation link. The link signed the reader in, which proves
 * it's them, so unlike Settings this doesn't ask for the current password.
 */
@Composable
fun SetPasswordScreen(email: String?, onDone: () -> Unit) {
    val supabase = LocalContainer.current.supabase
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val issue = passwordProblem(password) ?: if (password != repeated) "The passwords don’t match." else null
        if (issue != null) {
            problem = issue
            return
        }
        busy = true
        problem = null
        scope.launch {
            try {
                supabase.auth.updateUser { this.password = password }
                onDone()
            } catch (e: Exception) {
                problem = authMessage(e)
            } finally {
                busy = false
            }
        }
    }

    AuthCard("Set a password.") {
        Muted(if (email != null) "Choose a password for $email." else "Choose a new password.")
        Column(Modifier.padding(top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field(
                "New password",
                password,
                { password = it },
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            Field(
                "Repeat it",
                repeated,
                { repeated = it },
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                onDone = ::submit,
            )
            PrimaryButton(if (busy) "One moment…" else "Save password", ::submit, enabled = !busy, modifier = Modifier.padding(top = 10.dp))
        }
        problem?.let { FormMessage(it, Tone.Error, Modifier.padding(top = 14.dp)) }
        Gap(18)
        Muted("At least $MIN_PASSWORD_LENGTH characters.")
    }
}
