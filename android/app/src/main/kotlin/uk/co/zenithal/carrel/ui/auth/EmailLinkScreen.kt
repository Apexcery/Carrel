package uk.co.zenithal.carrel.ui.auth

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.EmailLink
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.theme.Carrel

private enum class LinkState { Checking, EmailChanged, Failed }

/**
 * A link from one of Carrel's emails, opened in the app. It confirms the link with Supabase, which signs the reader
 * in, then moves on: `onSignedIn` for a confirmed account, `onSetPassword` after a reset or invitation link. A changed
 * email and a failed link stay here until `onDone`.
 */
@Composable
fun EmailLinkScreen(
    link: EmailLink,
    onSignedIn: () -> Unit,
    onSetPassword: () -> Unit,
    onSendNewLink: () -> Unit,
    onDone: () -> Unit,
) {
    val supabase = LocalContainer.current.supabase
    var state by rememberSaveable(link) { mutableStateOf(LinkState.Checking) }

    // A link works once, so it's only sent while still checking (not again after the screen is recreated).
    LaunchedEffect(link) {
        if (state != LinkState.Checking) return@LaunchedEffect
        val tokenHash = link.tokenHash
        val type = link.type
        if (tokenHash == null || type == null) {
            state = LinkState.Failed
            return@LaunchedEffect
        }
        try {
            supabase.auth.verifyEmailOtp(type, tokenHash)
            when {
                link.setsPassword -> onSetPassword()
                type == OtpType.Email.EMAIL_CHANGE -> state = LinkState.EmailChanged
                // A confirmed sign-up: on to choosing a username.
                else -> onSignedIn()
            }
        } catch (e: Exception) {
            state = LinkState.Failed
        }
    }

    when (state) {
        LinkState.Checking -> AuthCard("One moment.") { Muted("Checking your link…") }
        LinkState.EmailChanged -> AuthCard("Email confirmed.") {
            Muted("If we also emailed your other address, confirm that link too: your email changes once both are confirmed.")
            LinkButton("Carry on", onDone, Modifier.padding(top = 22.dp), color = Carrel.colors.ink)
        }
        LinkState.Failed -> AuthCard("That link didn’t work.") {
            Muted("It may have expired or already been used. Links in Carrel’s emails last an hour.")
            if (link.setsPassword) {
                LinkButton("Send a new link", onSendNewLink, Modifier.padding(top = 22.dp), color = Carrel.colors.ink)
            } else {
                LinkButton("Carry on", onDone, Modifier.padding(top = 22.dp), color = Carrel.colors.ink)
            }
        }
    }
}
