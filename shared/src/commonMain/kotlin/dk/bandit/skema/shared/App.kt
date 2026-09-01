package dk.bandit.skema.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dk.bandit.skema.data.SkemaRepository
import dk.bandit.skema.data.ThemeChoice
import dk.bandit.skema.ui.AgendaScreen
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
    val model = remember { SkemaModel(SkemaRepository()) }

    LaunchedEffect(Unit) {
        model.loadCache()
        if (model.settings.refreshOnOpen && model.hasUrl) model.refresh()
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
                Screen.Agenda -> AgendaScreen(model)
                Screen.Settings -> SettingsScreen(model)
            }
        }
    }
}
