package dk.bandit.skema.data

import kotlinx.serialization.Serializable

/**
 * Everything the app remembers about how you want it to behave.
 *
 * The feed URL is a bearer credential: the token in it reads your whole
 * calendar with no login. It stays in the app's private storage and is never
 * sent anywhere except back to DTU.
 */
@Serializable
enum class ThemeChoice { System, Light, Dark }

/**
 * Which calendar the app is showing. The order is the zoom order, widest
 * first, because pinching in and out walks along it: Month, Week, Day. Agenda
 * sits outside that walk; it is a list rather than a grid, so it is reached
 * deliberately rather than by pinching past Day.
 */
@Serializable
enum class ViewMode { Month, Week, Day, Agenda }

@Serializable
data class SkemaSettings(
    val feedUrl: String = "",
    val theme: ThemeChoice = ThemeChoice.System,
    /** DTU lists most classes twice. Merge each pair into one entry. */
    val collapseDuplicates: Boolean = true,
    /** Pull the feed whenever the app comes to the front. */
    val refreshOnOpen: Boolean = true,

    /** Week is the default because it is the view the timetable is actually read in. */
    val view: ViewMode = ViewMode.Week,

    /**
     * Height of one hour in dp: the zoom level, driven by pinching the grid.
     * Persisted because a zoom you have to set again every launch is not a
     * setting, it is a chore.
     */
    val hourHeight: Float = 64f,

    /** Saturday and Sunday are empty in a university timetable most weeks. */
    val showWeekend: Boolean = false,

    /**
     * Course code to a pinned hue in degrees. Empty means every course takes
     * the hue the palette derives from how many courses there are. An entry
     * here is a colour the owner chose by hand and it is never reassigned.
     */
    val courseHues: Map<String, Float> = emptyMap(),
)
