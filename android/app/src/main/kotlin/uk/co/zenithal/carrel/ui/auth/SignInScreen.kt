package uk.co.zenithal.carrel.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.authMessage
import uk.co.zenithal.carrel.auth.passwordProblem
import uk.co.zenithal.carrel.ui.components.Field
import uk.co.zenithal.carrel.ui.components.FormMessage
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.PrimaryButton
import uk.co.zenithal.carrel.ui.components.Tone
import uk.co.zenithal.carrel.ui.theme.Carrel

enum class SignInMode(val title: String, val submit: String) {
    SignIn("Welcome back.", "Sign in"),
    SignUp("Take a seat.", "Create account"),
    Reset("Forgot your password?", "Send reset link"),
}

/**
 * Sign in, create an account, or ask for a password reset link, as on the website. Signing in moves on by itself
 * (the session changes); the other two leave a message to check email.
 */
@Composable
fun SignInScreen(initialMode: SignInMode, openLegal: (String) -> Unit) {
    val supabase = LocalContainer.current.supabase
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        val problem = when {
            email.isBlank() -> "Enter your email."
            mode != SignInMode.Reset && password.isEmpty() -> "Enter your password."
            mode == SignInMode.SignUp -> passwordProblem(password)
            else -> null
        }
        if (problem != null) {
            message = problem
            isError = true
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                val address = email.trim()
                when (mode) {
                    SignInMode.SignIn -> supabase.auth.signInWith(Email) {
                        this.email = address
                        this.password = password
                    }
                    SignInMode.SignUp -> {
                        supabase.auth.signUpWith(Email) {
                            this.email = address
                            this.password = password
                        }
                        message = "Check your inbox, and your spam folder, for a link to confirm your account."
                        isError = false
                    }
                    SignInMode.Reset -> {
                        supabase.auth.resetPasswordForEmail(address)
                        // Worded the same whether or not the address has an account, so it can't be used to find out who does.
                        message = "If there’s an account for that email, we’ve sent it a link to set a new password. Check your spam folder if it doesn’t arrive."
                        isError = false
                    }
                }
            } catch (e: Exception) {
                message = authMessage(e)
                isError = true
            } finally {
                busy = false
            }
        }
    }

    fun switchTo(next: SignInMode) {
        mode = next
        message = null
    }

    AuthCard(
        title = mode.title,
        below = {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LinkButton("Privacy", { openLegal("/privacy") })
                LinkButton("Copyright", { openLegal("/copyright") })
            }
        },
    ) {
        Muted(
            if (mode == SignInMode.Reset) "Enter your email and we’ll send you a link to set a new one."
            else "A quiet place to keep track of what you read.",
        )
        Column(Modifier.padding(top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field(
                "Email",
                email,
                { email = it },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (mode == SignInMode.Reset) ImeAction.Done else ImeAction.Next,
                ),
                onDone = ::submit,
            )
            if (mode != SignInMode.Reset) {
                Field(
                    "Password",
                    password,
                    { password = it },
                    password = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    onDone = ::submit,
                )
            }
            PrimaryButton(
                if (busy) "One moment…" else mode.submit,
                ::submit,
                enabled = !busy,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        message?.let { FormMessage(it, if (isError) Tone.Error else Tone.Neutral, Modifier.padding(top = 14.dp)) }

        if (mode == SignInMode.SignUp) {
            Gap(18)
            Muted("You must be 13 or over to create an account. See how Carrel handles your information in the privacy notice.")
            LinkButton("Read the privacy notice", { openLegal("/privacy") })
        }
        if (mode == SignInMode.SignIn) {
            Gap(16)
            LinkButton("Forgot your password?", { switchTo(SignInMode.Reset) })
        }
        Gap(16)
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Muted(
                when (mode) {
                    SignInMode.SignUp -> "Already have an account?"
                    SignInMode.Reset -> "Remembered it?"
                    SignInMode.SignIn -> "New here?"
                },
            )
            LinkButton(
                if (mode == SignInMode.SignIn) "Create an account" else "Sign in",
                { switchTo(if (mode == SignInMode.SignIn) SignInMode.SignUp else SignInMode.SignIn) },
                color = Carrel.colors.ink,
            )
        }
    }
}
