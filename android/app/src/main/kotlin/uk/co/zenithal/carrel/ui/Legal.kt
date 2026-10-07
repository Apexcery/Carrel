package uk.co.zenithal.carrel.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import uk.co.zenithal.carrel.BuildConfig

/**
 * Opens one of the website's pages (the privacy notice, the copyright page) in a browser panel over the app, so there's
 * one copy of the text and the reader stays in the app.
 */
fun openWebsitePage(context: Context, path: String, paper: Color) {
    val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(paper.toArgb()).build()
    CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colors)
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(BuildConfig.WEBSITE_URL + path))
}
