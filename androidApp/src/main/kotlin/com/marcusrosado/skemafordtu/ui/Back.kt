package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Composable

/**
 * Handles the system back gesture.
 *
 * Android's back is not a button on a screen, it is a gesture from the edge
 * that every app is expected to answer, and an app that does not answer it
 * exits. That is what this app was doing: opening Settings and swiping back
 * closed the whole thing, losing the calendar, because nothing had claimed
 * back and the activity was the only thing left on the stack.
 *
 * Every screen and every overlay that a person can get INTO has to offer a way
 * back OUT of it through this, in the reverse order they were opened: a sheet
 * before the screen under it, a screen before the app. `enabled` is what
 * orders them; a disabled handler is skipped and the next one up gets it.
 *
 * Delegates to the platform's own dispatcher, so the predictive back
 * animation Android draws is the real one rather than an imitation.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
