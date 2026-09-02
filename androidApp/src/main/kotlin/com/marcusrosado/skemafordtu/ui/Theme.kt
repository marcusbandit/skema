package com.marcusrosado.skemafordtu.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Three font sizes for the whole app, and no inline values anywhere else.
 * Hierarchy is carried by weight, colour and space instead, which carries it
 * better than two extra pixels ever did.
 */
object Type {
    val small = 13.sp
    val normal = 15.sp
    val large = 22.sp
}

/** One spacing step, everything else a multiple of it. */
object Space {
    val unit = 4.dp
    val tight = 6.dp
    val small = 10.dp
    val medium = 16.dp
    val large = 24.dp
}

object Radius {
    val chip = 8.dp
    val card = 14.dp
    val sheet = 20.dp
}

/**
 * The app's colour vocabulary, read off whatever scheme Material is showing.
 *
 * The views were written against these ten names, and the names are still the
 * right vocabulary for a calendar: a page, a card on it, a sunken well in the
 * card, ink and quieter ink, the accent and its soft form. What changed is
 * where the values come from. They used to be hand picked hexes; they are now
 * a reading of [MaterialTheme.colorScheme], so on Android 12 and up the app
 * wears the phone's own dynamic colour and every part of it, stock or custom,
 * agrees about what the accent is.
 */
@Immutable
data class SkemaColors(
    val background: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val hairline: Color,
    val isDark: Boolean,
)

/**
 * The vocabulary, in Material's terms. Cards sit on surfaceContainerLow, the
 * tone Material's own Card uses, so a custom card and a stock one are the
 * same colour; the well under a segmented row is one step up from that.
 */
fun facade(scheme: ColorScheme, dark: Boolean): SkemaColors = SkemaColors(
    background = scheme.background,
    surface = scheme.surfaceContainerLow,
    surfaceSunken = scheme.surfaceContainerHigh,
    ink = scheme.onBackground,
    inkMuted = scheme.onSurfaceVariant,
    accent = scheme.primary,
    accentStrong = scheme.error,
    accentSoft = scheme.secondaryContainer,
    onAccent = scheme.onPrimary,
    hairline = scheme.outlineVariant,
    isDark = dark,
)

/**
 * DTU's red, for phones without dynamic colour and for the tests.
 *
 * The university's own pages mix 990000, 7a0000 and d10000 more or less at
 * random; here 7a0000 is the ink red for light backgrounds, d10000 the signal
 * red, ff6161 the red that survives a dark background. The neutrals are warm,
 * a few degrees under the red's hue, which is what stops the greys reading as
 * the neutral grey every theme ships with.
 */
fun fallbackScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFFF6161),
        onPrimary = Color(0xFF310B0A),
        primaryContainer = Color(0xFF452B28),
        onPrimaryContainer = Color(0xFFFF6161),
        secondaryContainer = Color(0xFF452B28),
        onSecondaryContainer = Color(0xFFFF6161),
        background = Color(0xFF292423),
        onBackground = Color(0xFFF1ECEA),
        surface = Color(0xFF292423),
        onSurface = Color(0xFFF1ECEA),
        surfaceContainerLow = Color(0xFF383130),
        surfaceContainer = Color(0xFF3E3736),
        surfaceContainerHigh = Color(0xFF453D3C),
        surfaceContainerHighest = Color(0xFF4C4342),
        surfaceVariant = Color(0xFF452B28),
        onSurfaceVariant = Color(0xFFA29593),
        outline = Color(0xFF483E3D),
        outlineVariant = Color(0xFF483E3D),
        error = Color(0xFFFF6161),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF7A0000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF6E7E7),
        onPrimaryContainer = Color(0xFF7A0000),
        secondaryContainer = Color(0xFFF6E7E7),
        onSecondaryContainer = Color(0xFF7A0000),
        background = Color(0xFFF4F1EE),
        onBackground = Color(0xFF1B1416),
        surface = Color(0xFFF4F1EE),
        onSurface = Color(0xFF1B1416),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFF0EBE7),
        surfaceContainerHigh = Color(0xFFEAE5E0),
        surfaceContainerHighest = Color(0xFFE2DBD5),
        surfaceVariant = Color(0xFFF6E7E7),
        onSurfaceVariant = Color(0xFF6E6462),
        outline = Color(0xFFE2DBD5),
        outlineVariant = Color(0xFFE2DBD5),
        error = Color(0xFFD10000),
    )
}

/**
 * The vocabulary as the fallback would give it. CourseColorsTest reads the
 * background off it to check an event block is findable on the page; it runs
 * on the JVM, where there is no phone to take a dynamic scheme from.
 */
fun skemaColors(dark: Boolean): SkemaColors = facade(fallbackScheme(dark), dark)

private val LocalDark = staticCompositionLocalOf { false }

val skema: SkemaColors
    @Composable @ReadOnlyComposable get() = facade(MaterialTheme.colorScheme, LocalDark.current)

/**
 * The corners every Material part wears. A plain rounded rectangle is G1 and
 * the eye reads the join as a pinch; these are squircles, so a stock button
 * and a hand drawn card have the same corner. The five sizes are Material's
 * own tokens, so a component asking for its usual shape gets the usual size
 * with the house curve.
 */
private val SkemaShapes = Shapes(
    extraSmall = SquircleShape(4.dp),
    small = SquircleShape(8.dp),
    medium = SquircleShape(12.dp),
    large = SquircleShape(16.dp),
    extraLarge = SquircleShape(28.dp),
)

@Composable
fun SkemaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // The phone's own colour where the phone has one, DTU's red where it has
    // not. Dynamic colour arrived in Android 12; below that the scheme is the
    // same one the tests see.
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> fallbackScheme(dark)
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }

    // No font family is named anywhere, so every face is the platform's own
    // system sans. Nothing is downloaded and nothing is a serif. The three
    // sizes are spread over Material's roles so a stock part and a custom one
    // set the same words at the same size.
    val base = Typography()
    val typography = base.copy(
        headlineSmall = base.headlineSmall.copy(fontSize = Type.large, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = Type.large, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = Type.normal, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontSize = Type.normal, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = Type.normal),
        bodyMedium = base.bodyMedium.copy(fontSize = Type.normal),
        bodySmall = base.bodySmall.copy(fontSize = Type.small),
        labelLarge = base.labelLarge.copy(fontSize = Type.normal, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = Type.small),
        labelSmall = base.labelSmall.copy(fontSize = Type.small),
    )

    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = SkemaShapes, content = content)
    }
}
