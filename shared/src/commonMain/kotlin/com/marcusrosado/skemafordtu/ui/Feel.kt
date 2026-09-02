package com.marcusrosado.skemafordtu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The small physical acknowledgements that separate an app that responds from
 * an app that merely updates.
 *
 * This is most of what people mean when they say a cross platform app does not
 * feel native. On Android, Compose here IS the native toolkit, the same
 * Jetpack Compose Google ships, so the gap is not the framework: it is that
 * nothing in this app has ever asked the phone to tick. A view change, a
 * snapped sheet and a picked event are all moments the hardware can confirm,
 * and confirming them costs one call.
 *
 * Kept behind one interface so a platform without a vibrator, the desktop,
 * silently does nothing rather than each call site guarding itself.
 */
interface Feel {
    /** A selection moved: a tab, a segment, a day. The lightest tick there is. */
    fun tick()

    /** Something committed or snapped into place: a sheet state, a dismissal. */
    fun thud()
}

private object NoFeel : Feel {
    override fun tick() = Unit
    override fun thud() = Unit
}

val LocalFeel = staticCompositionLocalOf<Feel> { NoFeel }

/**
 * Wraps the platform's haptics. `TextHandleMove` is the lightest event Compose
 * exposes and is what a selection tick should use; `LongPress` is heavier and
 * reads as a commit. Compose Multiplatform maps both to nothing where there is
 * no vibrator, so no platform check is needed here.
 */
@Composable
fun rememberFeel(): Feel {
    val haptics: HapticFeedback = LocalHapticFeedback.current
    return object : Feel {
        override fun tick() = haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        override fun thud() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
