package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
 * DTU's red, used consistently for once. The university's own pages mix
 * 990000, 7a0000 and d10000 more or less at random; here 7a0000 is the ink
 * red for light backgrounds, d10000 the signal red, ff6161 the red that
 * survives a dark background, 860404 the pressed state.
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

private val Light = SkemaColors(
    background = Color(0xFFF4F1EE),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEAE5E0),
    ink = Color(0xFF1B1416),
    inkMuted = Color(0xFF6E6462),
    accent = Color(0xFF7A0000),
    accentStrong = Color(0xFFD10000),
    accentSoft = Color(0xFFF6E7E7),
    onAccent = Color(0xFFFFFFFF),
    hairline = Color(0xFFE2DBD5),
    isDark = false,
)

/**
 * The dark set used to sit at background 121011, surface 1C1819, sunken 0C0A0B,
 * which measures L 0.176 / 0.214 / 0.148 in OKLab. That is near black, and it
 * left surface only 1.08:1 off background, so a card did not read as sitting
 * on the page; both were the same flat dark. The whole set moves up a step, to
 * L 0.265 / 0.320 / 0.213. Surface now stands 1.20:1 and sunken 1.15:1 against
 * background, and sunken lands above the old background rather than falling
 * further towards black.
 *
 * Every neutral here is generated from OKLCH hue 25 with chroma climbing 0.007
 * to 0.014 as lightness climbs, so the warmth holds up at the higher L instead
 * of washing out. Hue 25 is a few degrees under the accent's measured 29.2. The
 * hexes that were here before drifted between hue 345.7 and hue 8.0 because
 * each was picked by hand; one hue makes the red lean deliberate, and it is
 * that lean which stops these greys reading as the neutral grey every dark
 * theme ships with.
 *
 * hairline goes to L 0.375: 1.48:1 on background and 1.23:1 on surface, up from
 * 1.30 and 1.20, so it survives the lift on both. inkMuted goes to L 0.680,
 * which holds 5.29:1 on the lighter background while staying 2.47:1 clear of
 * ink, close to the 2.62:1 the two had before.
 *
 * accentSoft and onAccent move with the rest so they do not end up the two
 * darkest things left: accentSoft L 0.250 to 0.320 with chroma 0.029 to 0.040,
 * which keeps it a visible maroon rather than a wash at the higher lightness,
 * and onAccent L 0.190 to 0.215, still 6.03:1 on the accent it is written on.
 */
private val Dark = SkemaColors(
    background = Color(0xFF292423),
    surface = Color(0xFF383130),
    surfaceSunken = Color(0xFF1C1817),
    ink = Color(0xFFF1ECEA),
    inkMuted = Color(0xFFA29593),
    accent = Color(0xFFFF6161),
    accentStrong = Color(0xFFFF6161),
    accentSoft = Color(0xFF452B28),
    onAccent = Color(0xFF310B0A),
    hairline = Color(0xFF483E3D),
    isDark = true,
)

/**
 * The two sets, by theme. [SkemaTheme] chooses with it, and CourseColorsTest
 * reads the background off it to check an event block is findable on the page.
 */
fun skemaColors(dark: Boolean): SkemaColors = if (dark) Dark else Light

val LocalSkemaColors = staticCompositionLocalOf { Light }

val skema: SkemaColors
    @Composable @ReadOnlyComposable get() = LocalSkemaColors.current

@Composable
fun SkemaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = skemaColors(dark)

    // Material3 only exists here so the Switch and the text field look right.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.accent,
            background = c.background,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.accentSoft,
            onSurfaceVariant = c.inkMuted,
            outline = c.hairline,
            outlineVariant = c.hairline,
            error = c.accentStrong,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.accent,
            background = c.background,
            onBackground = c.ink,
            surface = c.surface,
            onSurface = c.ink,
            surfaceVariant = c.accentSoft,
            onSurfaceVariant = c.inkMuted,
            outline = c.hairline,
            outlineVariant = c.hairline,
            error = c.accentStrong,
        )
    }

    // No font family is named anywhere, so every face is the platform's own
    // system sans. Nothing is downloaded and nothing is a serif.
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = Type.normal),
        bodyMedium = base.bodyMedium.copy(fontSize = Type.normal),
        bodySmall = base.bodySmall.copy(fontSize = Type.small),
        labelLarge = base.labelLarge.copy(fontSize = Type.normal, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = Type.small),
        titleLarge = base.titleLarge.copy(fontSize = Type.large, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = Type.normal, fontWeight = FontWeight.Medium),
    )

    CompositionLocalProvider(LocalSkemaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
