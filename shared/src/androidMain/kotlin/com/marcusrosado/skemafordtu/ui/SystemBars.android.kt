package com.marcusrosado.skemafordtu.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The same call the activity makes when it is created, made again whenever
 * the theme changes, with the theme handed in instead of read off the phone.
 * That is what enableEdgeToEdge's detectDarkMode parameter exists for, and it
 * is how Google's own Now in Android sample keeps its bars in step with an
 * in-app theme switch.
 *
 * Transparent scrims both ways, because the app paints its own background
 * under the bars and a tinted band over that would be a second, wrong colour.
 */
@Composable
actual fun SystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
        )
        onDispose {}
    }
}
