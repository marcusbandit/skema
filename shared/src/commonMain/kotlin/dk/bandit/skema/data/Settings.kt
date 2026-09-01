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

@Serializable
data class SkemaSettings(
    val feedUrl: String = "",
    val theme: ThemeChoice = ThemeChoice.System,
    /** DTU lists most classes twice. Fold each pair into one entry. */
    val collapseDuplicates: Boolean = true,
    /** Pull the feed whenever the app comes to the front. */
    val refreshOnOpen: Boolean = true,
)
