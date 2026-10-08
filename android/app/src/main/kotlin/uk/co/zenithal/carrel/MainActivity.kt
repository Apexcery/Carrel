package uk.co.zenithal.carrel

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import uk.co.zenithal.carrel.auth.EmailLink
import uk.co.zenithal.carrel.ui.CarrelRoot
import uk.co.zenithal.carrel.ui.theme.CarrelTheme
import uk.co.zenithal.carrel.ui.theme.ThemeChoice

/** The app's shared services, for any screen that needs them. */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

class MainActivity : ComponentActivity() {
    /** A link from one of Carrel's emails that opened the app, until it's dealt with. */
    private var link by mutableStateOf<EmailLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch reads its link; after recreation (rotation) the link was already used.
        if (savedInstanceState == null) link = EmailLink.from(intent?.data)
        val container = (application as CarrelApp).container
        setContent {
            val theme by container.appearance.theme.collectAsState()
            val accent by container.appearance.accent.collectAsState()
            val dark = when (theme) {
                ThemeChoice.Auto -> isSystemInDarkTheme()
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
            }
            // The status bar's icons follow the chosen theme, not the phone's, so they show against the paper.
            DisposableEffect(dark) {
                edgeToEdge(dark)
                onDispose {}
            }
            CompositionLocalProvider(LocalContainer provides container) {
                CarrelTheme(dark, accent) {
                    CarrelRoot(link, onLinkHandled = { link = null })
                }
            }
        }
    }

    private fun edgeToEdge(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            // Android's own scrims, for older phones that can't show dark navigation buttons.
            navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xff, 0xff, 0xff), Color.argb(0x80, 0x1b, 0x1b, 0x1b)) { dark },
        )
        // The app's own paper shows behind the system's navigation buttons, rather than a white strip.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        EmailLink.from(intent.data)?.let { link = it }
    }
}
