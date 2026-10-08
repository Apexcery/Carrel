package uk.co.zenithal.carrel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import uk.co.zenithal.carrel.LocalContainer
import uk.co.zenithal.carrel.auth.EmailLink
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.Destination
import uk.co.zenithal.carrel.data.Loaded
import uk.co.zenithal.carrel.data.PROFILE_PATH
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.ui.auth.ChooseUsernameScreen
import uk.co.zenithal.carrel.ui.auth.EmailLinkScreen
import uk.co.zenithal.carrel.ui.auth.SetPasswordScreen
import uk.co.zenithal.carrel.ui.auth.SignInMode
import uk.co.zenithal.carrel.ui.components.ErrorNotice
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * Everything the app shows, as the website's App does: browsing is open to everyone, and a signed-in reader without a
 * username chooses one first. A link from one of Carrel's emails (`link`) takes over until it's dealt with. Any other
 * link, share, or shortcut (`destination`) waits for the tabs to show, then opens its page.
 */
@Composable
fun CarrelRoot(link: EmailLink?, onLinkHandled: () -> Unit, destination: Destination?, onDestinationReached: () -> Unit) {
    val container = LocalContainer.current
    val session by container.session.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    // After a reset or invitation link signs the reader in, they choose a password before anything else.
    var settingPassword by rememberSaveable { mutableStateOf(false) }

    when (val current = session) {
        Session.Loading -> Blank()
        else -> when {
            link != null -> EmailLinkScreen(
                link = link,
                onSignedIn = onLinkHandled,
                onSetPassword = {
                    settingPassword = true
                    onLinkHandled()
                },
                onSendNewLink = {
                    onLinkHandled()
                    nav.navigate(SignInRoute(SignInMode.Reset))
                },
                onDone = onLinkHandled,
            )
            current is Session.SignedIn && settingPassword ->
                SetPasswordScreen(current.email) { settingPassword = false }
            current is Session.SignedIn -> SignedIn(current) { profile -> MainScreen(nav, current, profile, destination, onDestinationReached) }
            else -> MainScreen(nav, current, profile = null, destination, onDestinationReached)
        }
    }
}

/** Loads the reader's profile, and asks for a username until they have one. */
@Composable
private fun SignedIn(session: Session.SignedIn, content: @Composable (Profile) -> Unit) {
    val store = LocalContainer.current.store
    var attempt by remember { mutableIntStateOf(0) }
    val profile by remember(session.userId, attempt) { store.observe(PROFILE_PATH, Profile.serializer()) }
        .collectAsStateWithLifecycle(Loaded(null, refreshing = true, error = null))
    val data = profile.data
    when {
        data == null && profile.error != null -> Box(Modifier.fillMaxSize().background(Carrel.colors.paper).safeDrawingPadding()) {
            ErrorNotice(profile.error?.message.orEmpty(), { attempt++ }, Modifier.padding(horizontal = 16.dp))
        }
        data == null -> Blank()
        data.username == null -> ChooseUsernameScreen()
        else -> content(data)
    }
}

/** Plain paper, while there's nothing to show yet. */
@Composable
private fun Blank() = Box(Modifier.fillMaxSize().background(Carrel.colors.paper))
