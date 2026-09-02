package com.marcusrosado.skemafordtu

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.marcusrosado.skemafordtu.data.SkemaRepository
import com.marcusrosado.skemafordtu.data.ThemeChoice
import com.marcusrosado.skemafordtu.ui.CalendarShell
import com.marcusrosado.skemafordtu.ui.CoursePalette
import com.marcusrosado.skemafordtu.ui.Screen
import com.marcusrosado.skemafordtu.ui.SemesterView
import com.marcusrosado.skemafordtu.ui.SettingsScreen
import com.marcusrosado.skemafordtu.ui.SkemaModel
import com.marcusrosado.skemafordtu.ui.SkemaTheme
import com.marcusrosado.skemafordtu.ui.SystemBars

/**
 * The whole app. The activity calls this and nothing else.
 *
 * SkemaAndroid.init(applicationContext) has to have run first, because
 * storage lives in the shared module, which cannot reach a Context.
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
        // Position is restored in the model's constructor, not here: an
        // effect would race the save debounce. The fetch is not started here
        // either, the shell syncs on arrival and then on an interval, so
        // opening the app is always a sync and there is nothing to opt out of.
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

    // One palette for the whole app, built here so the calendar and the
    // semester screen cannot disagree about what colour a course is. Rebuilt
    // only when the course set, the pinned hues or the theme change: the hue
    // assignment re-spreads every course, so doing it per frame would be
    // visible work for no reason.
    // Built per semester, not over every course the feed has ever mentioned.
    // Spreading fifteen courses around one circle left this term's five
    // crowded together for the sake of subjects finished a year ago. Two
    // courses in different terms sharing a colour costs nothing, because they
    // are never on screen together.
    val terms = model.terms
    val palette = remember(terms, model.settings.courseHues, dark) {
        CoursePalette.bySemester(terms.map { model.coursesIn(it) }, model.settings.courseHues, dark)
    }

    SkemaTheme(dark) {
        // The bars' icons follow the theme resolved above, not the phone's
        // setting. The two differ whenever the theme is chosen by hand.
        SystemBars(dark)
        // Android draws edge to edge from API 35 whether you ask for it or
        // not. Every screen is a scaffold, so each keeps its content out of
        // the bars itself and lets its own bar and drawer run under them.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (model.screen) {
                Screen.Calendar -> CalendarShell(model, palette)
                Screen.Semesters -> SemesterView(model, palette)
                Screen.Settings -> SettingsScreen(model)
            }
        }
    }
}
