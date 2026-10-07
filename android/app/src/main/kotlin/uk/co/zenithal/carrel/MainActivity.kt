package uk.co.zenithal.carrel

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import uk.co.zenithal.carrel.auth.EmailLink
import uk.co.zenithal.carrel.ui.CarrelRoot
import uk.co.zenithal.carrel.ui.theme.CarrelTheme

/** The app's shared services, for any screen that needs them. */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }

class MainActivity : ComponentActivity() {
    /** A link from one of Carrel's emails that opened the app, until it's dealt with. */
    private var link by mutableStateOf<EmailLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The app's own paper shows behind the system's navigation buttons, rather than a white strip.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        // Only a fresh launch reads its link; after recreation (rotation) the link was already used.
        if (savedInstanceState == null) link = EmailLink.from(intent?.data)
        val container = (application as CarrelApp).container
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                CarrelTheme {
                    CarrelRoot(link, onLinkHandled = { link = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        EmailLink.from(intent.data)?.let { link = it }
    }
}
