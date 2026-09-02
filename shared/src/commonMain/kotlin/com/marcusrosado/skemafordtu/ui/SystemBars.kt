package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Composable

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
 * it, so the bars and the page can never disagree.
 */
@Composable
expect fun SystemBars(dark: Boolean)
