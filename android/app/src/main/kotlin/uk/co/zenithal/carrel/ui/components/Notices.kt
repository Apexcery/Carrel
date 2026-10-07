package uk.co.zenithal.carrel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.ui.theme.Carrel

/** Something failed to load: the reason, and a way to try again. */
@Composable
fun ErrorNotice(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker("Not on the shelf")
        Text(message, style = Carrel.type.body, color = Carrel.colors.ink)
        LinkButton("Try again", onRetry, color = Carrel.colors.ink)
    }
}

/** The website's prompt for signed-out readers: a title, why to sign in, and the ways to. */
@Composable
fun SignInPrompt(title: String, text: String, onSignIn: () -> Unit, onSignUp: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = Carrel.type.heading, color = Carrel.colors.ink)
        Text(text, style = Carrel.type.body, color = Carrel.colors.inkSoft)
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton("Sign in", onSignIn)
            LinkButton("Create an account", onSignUp, color = Carrel.colors.ink)
        }
    }
}
