package dk.bandit.skema.ui

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

private val Dark = SkemaColors(
    background = Color(0xFF121011),
    surface = Color(0xFF1C1819),
    surfaceSunken = Color(0xFF0C0A0B),
    ink = Color(0xFFF1ECEA),
    inkMuted = Color(0xFF9C918E),
    accent = Color(0xFFFF6161),
    accentStrong = Color(0xFFFF6161),
    accentSoft = Color(0xFF2E1C1D),
    onAccent = Color(0xFF2A0505),
    hairline = Color(0xFF2E2728),
    isDark = true,
)

val LocalSkemaColors = staticCompositionLocalOf { Light }

val skema: SkemaColors
    @Composable @ReadOnlyComposable get() = LocalSkemaColors.current

@Composable
fun SkemaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light

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
