package com.marcusrosado.skemafordtu.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Tells the platform which way up to draw its own bars' icons.
 *
 * The app draws edge to edge, so the status bar's clock and the navigation
 * bar's handle sit on the app's background, and the platform cannot see what
 * colour that is. Left to itself Android colours them from the PHONE's dark
 * mode, which is right until the app's theme disagrees with it: the app set
 * to Light on a phone set to Dark drew a white clock on a near white page,
 * and the clock was gone.
 *
 * Called with the theme the app resolved to, from the one place that resolves
 * it, so the bars and the page can never disagree. It is the same call the
 * activity makes when it is created, made again whenever the theme changes,
 * with the theme handed in instead of read off the phone. That is what
 * enableEdgeToEdge's detectDarkMode parameter exists for, and it is how
 * Google's own Now in Android sample keeps its bars in step with an in-app
 * theme switch.
 *
 * Transparent scrims both ways, because the app paints its own background
 * under the bars and a tinted band over that would be a second, wrong colour.
 */
@Composable
fun SystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
        onDispose {}
    }
}
