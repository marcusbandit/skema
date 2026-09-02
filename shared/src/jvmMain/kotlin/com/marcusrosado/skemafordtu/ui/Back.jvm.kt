package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Composable

/** No back gesture on the desktop; a window is closed by its own chrome. */
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
