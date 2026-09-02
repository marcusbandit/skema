package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CalendarRange
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Columns3
import com.composables.icons.lucide.Database
import com.composables.icons.lucide.Grid3x3
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.List
import com.composables.icons.lucide.LocateFixed
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RectangleVertical
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.SunMoon
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.ExclamationTriangle

/**
 * Every icon in the app, named by what it means rather than what it looks
 * like.
 *
 * One enum and one drawing function, for the same reason there is one corner
 * primitive and three font sizes: the alternative is each screen reaching for
 * whichever glyph it liked, and an app whose back arrow is a different shape
 * on two screens. Swapping the whole set, or one member of it, is an edit in
 * a single file.
 */
enum class Ico {
    Month, Week, Day, Agenda,
    Today, Settings, Sync, Close, Expand, Collapse,
    Back, Forward, Room, Clock, Palette, Reset, Clash, Search, Check,

    // Added for the settings screen: its section headings and its rows.
    Link, Tune, Storage, Trash, Warn, Theme, Layout, Fold,

    // Added for the sidebar. The four views again, drawn as what they lay out
    // rather than as calendars, plus the control that opens the sidebar when
    // it is a drawer. Separate members from Month/Week/Day above because those
    // four are calendars wherever they appear, and the settings screen leans
    // on that: Ico.Day marks an enrolment date, not the day view.
    ViewMonth, ViewWeek, ViewDay, ViewAgenda, Menu,
}

/**
 * Two packs, split by what a glyph is saying rather than by taste.
 *
 * Lucide draws the nouns and the navigation, which is almost all of it. One
 * 24px grid at a single 2px stroke weight, so a row of its icons reads as one
 * alphabet and a hairline sits beside text without out-shouting it. Where the
 * packs offer the same idea Lucide won on shape: its calendar family separates
 * month, week and day by what is drawn inside the frame, which is exactly the
 * distinction the view switcher is making.
 *
 * Font Awesome Solid draws the two verdicts: the tick that says settled and
 * the triangle that says wrong. A verdict wants to look stamped rather than
 * sketched, and being solid is what lets it survive inside a tag, where an
 * outline scaled down to a hairline sinks into a saturated fill. Neither ever
 * shares a row with an outline glyph, which is what keeps the mix invisible.
 */
private val Ico.vector: ImageVector
    get() = when (this) {
        // The four views. Grid of days, a span of days, one day with hours,
        // and the list that is not a grid at all.
        Ico.Month -> Lucide.CalendarDays
        Ico.Week -> Lucide.CalendarRange
        Ico.Day -> Lucide.CalendarClock
        Ico.Agenda -> Lucide.List

        // Today is an action, not a fifth view: it recentres on now, so it
        // gets the locate crosshair rather than another calendar.
        Ico.Today -> Lucide.LocateFixed
        Ico.Settings -> Lucide.Settings
        Ico.Sync -> Lucide.RefreshCw
        Ico.Close -> Lucide.X
        Ico.Expand -> Lucide.ChevronDown
        Ico.Collapse -> Lucide.ChevronUp
        Ico.Back -> Lucide.ChevronLeft
        Ico.Forward -> Lucide.ChevronRight
        Ico.Room -> Lucide.MapPin
        Ico.Clock -> Lucide.Clock
        Ico.Palette -> Lucide.Palette
        Ico.Reset -> Lucide.RotateCcw
        Ico.Search -> Lucide.Search
        Ico.Link -> Lucide.Link2
        Ico.Tune -> Lucide.SlidersHorizontal
        Ico.Storage -> Lucide.Database
        Ico.Trash -> Lucide.Trash2
        Ico.Theme -> Lucide.SunMoon
        Ico.Layout -> Lucide.LayoutGrid
        Ico.Fold -> Lucide.Layers

        // Warn is a caution, not a verdict: it sits on paper beside muted
        // status text, so it stays in the outline alphabet.
        Ico.Warn -> Lucide.CircleAlert

        // The sidebar's four, in Google Calendar's vocabulary: one column for
        // the day, several for the week, a grid for the month, stacked rows
        // for the agenda. Three of them are literally the same 24px rounded
        // frame with different lines drawn inside it, and the fourth is that
        // frame narrowed to a single column, so the set reads as one alphabet
        // saying one thing, which is how many pieces the screen is cut into.
        // A calendar glyph cannot say that: four calendars side by side all
        // read as "calendar" first and the difference second.
        Ico.ViewMonth -> Lucide.Grid3x3
        Ico.ViewWeek -> Lucide.Columns3
        Ico.ViewDay -> Lucide.RectangleVertical
        Ico.ViewAgenda -> Lucide.Rows3

        Ico.Menu -> Lucide.Menu

        // The two verdicts.
        Ico.Check -> FontAwesomeIcons.Solid.Check
        Ico.Clash -> FontAwesomeIcons.Solid.ExclamationTriangle
    }

/**
 * The icon size, taken from the type scale instead of chosen: 18dp is close
 * enough to the line height of Type.normal that a glyph and the body text it
 * labels sit on the same rhythm.
 */
val IconSize: Dp = 18.dp

/**
 * The quiet size, for glyphs beside Type.small. Scaled by the ratio the type
 * scale already uses, so changing a font size moves the icons with it and
 * there is no third number to keep in step.
 */
val IconSizeQuiet: Dp = IconSize * (Type.small.value / Type.normal.value)

/**
 * Draws one icon, tinted, at the given size.
 *
 * A description is only worth passing when the glyph is the whole control. An
 * icon next to its own label wants none: a screen reader would read the same
 * word twice. Null is therefore the right default, and the icon-only controls
 * in Parts.kt demand a description instead of defaulting it.
 */
@Composable
fun Glyph(
    ico: Ico,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = IconSize,
    description: String? = null,
) {
    val colour = if (tint == Color.Unspecified) skema.ink else tint
    Image(
        imageVector = ico.vector,
        contentDescription = description,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(colour),
    )
}
