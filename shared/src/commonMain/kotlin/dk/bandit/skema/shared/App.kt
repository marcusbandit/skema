package dk.bandit.skema.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dk.bandit.skema.data.SkemaRepository
import dk.bandit.skema.data.ThemeChoice
import dk.bandit.skema.ui.CalendarShell
import dk.bandit.skema.ui.Screen
import dk.bandit.skema.ui.SettingsScreen
import dk.bandit.skema.ui.SkemaModel
import dk.bandit.skema.ui.SkemaTheme
import dk.bandit.skema.ui.skema

/**
 * The whole app. Android and desktop both call this and nothing else.
 *
 * On Android, SkemaAndroid.init(applicationContext) has to have run first,
 * because storage there needs a Context and common code cannot reach one.
 */
@Composable
fun App() {
    // The model needs a scope to debounce writing settings to disk. The
    // composition's own scope is right: it dies with the app, and a pending
    // save that never lands because the app went away is the correct outcome.
    val scope = rememberCoroutineScope()
    val model = remember { SkemaModel(SkemaRepository(), scope) }

    LaunchedEffect(Unit) {
        model.loadCache()
        // Position is restored before the first frame so the calendar opens on
        // the right week rather than opening on today and then jumping. The
        // fetch is not started here any more: the shell syncs on its own, on
        // arrival and then on an interval, so opening the app is always a sync
        // and there is nothing left to opt out of.
        model.restoreSession()
    }

    // Anything that changes "where you are" re-arms the debounced save. Keyed
    // declaratively rather than called from each navigation site, so a new way
    // to move around the calendar is remembered without anyone remembering to
    // wire it up.
    LaunchedEffect(model.anchor, model.selected, model.view, model.hourHeight) {
        model.rememberPosition()
    }

    val dark = when (model.settings.theme) {
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
        ThemeChoice.System -> isSystemInDarkTheme()
    }

    SkemaTheme(dark) {
        // Android draws edge to edge from API 35 whether you ask for it or
        // not, so the whole app keeps out of the status and gesture bars.
        Box(Modifier.fillMaxSize().background(skema.background).safeDrawingPadding()) {
            when (model.screen) {
                Screen.Calendar -> CalendarShell(model)
                Screen.Settings -> SettingsScreen(model)
            }
        }
    }
}
