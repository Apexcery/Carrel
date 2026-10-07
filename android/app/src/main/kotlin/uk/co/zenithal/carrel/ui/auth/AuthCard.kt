package uk.co.zenithal.carrel.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import uk.co.zenithal.carrel.ui.components.Kicker
import uk.co.zenithal.carrel.ui.theme.Carrel

/**
 * The website's sign-in card: raised paper with an accent rule along the top, a kicker, and a title, centred on the
 * page. `below` sits under the card (the legal links).
 */
@Composable
fun AuthCard(
    title: String,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Carrel.colors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(6.dp, clip = false)
                    .background(colors.paperRaised),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(colors.accent))
                Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 33.dp, bottom = 30.dp)) {
                    Kicker("Carrel")
                    Text(
                        title,
                        style = Carrel.type.signInTitle,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    )
                    content()
                }
            }
            below()
        }
    }
}

/** Soft-ink running text, the website's .muted. */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Carrel.type.body, color = Carrel.colors.inkSoft, modifier = modifier)
