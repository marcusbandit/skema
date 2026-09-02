package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Composable

/**
 * Delegates to the platform's own dispatcher, so the predictive back
 * animation Android draws is the real one rather than an imitation.
 */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
