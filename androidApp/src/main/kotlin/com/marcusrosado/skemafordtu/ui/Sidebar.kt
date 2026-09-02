package com.marcusrosado.skemafordtu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcusrosado.skemafordtu.data.ViewMode
import kotlinx.coroutines.launch

/**
 * The sidebar, in Material's two presentations of the same list.
 *
 * On a compact width it is a modal navigation drawer: the stock one, with
 * its scrim, its slide, its edge swipe and its predictive back. On a medium
 * or expanded width it is a navigation rail down the left edge, always
 * there. Both are built from one list of rows, so a fifth view arrives in
 * both without anyone editing this file twice.
 *
 * It used to be a hand drawn strip that pushed the calendar aside. The push
 * was the point once; now the point is that it behaves like every other
 * drawer on the phone, and Material's does.
 */

/**
 * The rail's width, Material's own. The shell's panes measure against what
 * is left after it, which is what keeps a day column on screen.
 */
internal val RAIL_WIDTH = 80.dp

/** One row of the sidebar. */
private class SidebarEntry(
    val ico: Ico,
    val label: String,
    val selected: Boolean,
    val onPick: () -> Unit,
)

/** The glyph each view answers to. See Icons.kt for why these four shapes. */
private fun glyphFor(mode: ViewMode): Ico = when (mode) {
    ViewMode.Month -> Ico.ViewMonth
    ViewMode.Week -> Ico.ViewWeek
    ViewMode.Day -> Ico.ViewDay
    ViewMode.Agenda -> Ico.ViewAgenda
}

/**
 * What the sidebar holds, in groups.
 *
 * The views are read off ViewMode.entries rather than written out, so a fifth
 * view arrives here as a row without anyone editing this file, and the rules
 * between groups are counted off the groups. The second group is the two
 * places that are not views: they leave the calendar rather than change it,
 * which is why nothing in it is ever the selected row.
 */
private fun sidebarGroups(model: SkemaModel, onNavigate: () -> Unit): List<List<SidebarEntry>> =
    listOf(
        ViewMode.entries.map { mode ->
            SidebarEntry(glyphFor(mode), mode.name, model.view == mode) {
                model.setView(mode)
                onNavigate()
            }
        },
        listOf(
            // Lucide's stacked layers: a list of semesters is a stack of terms,
            // and this is the one glyph in the set that draws a stack.
            SidebarEntry(Ico.Fold, "Semesters", false) {
                model.screen = Screen.Semesters
                onNavigate()
            },
            SidebarEntry(Ico.Settings, "Settings", false) {
                model.screen = Screen.Settings
                onNavigate()
            },
        ),
    )

/**
 * The compact presentation: Material's modal drawer around the content.
 *
 * The sheet is rounded on its trailing side only, through the house
 * primitive, so its leading edge sits flush against the screen's. The app's
 * name heads it the way a drawer names the thing it belongs to; it is the
 * only place the name is printed, the top bar being spent on where in the
 * calendar you are.
 */
@Composable
fun SidebarDrawer(
    model: SkemaModel,
    state: DrawerState,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.close() } }
    ModalNavigationDrawer(
        drawerState = state,
        drawerContent = {
            // Given the drawer state, so the sheet wires up predictive back
            // itself: the overload without it draws the same sheet and lets
            // back fall through to the activity.
            ModalDrawerSheet(drawerState = state, drawerShape = SquircleShape.end(DRAWER_CORNER)) {
                Text(
                    "Skema",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = Type.large,
                    maxLines = 1,
                    modifier = Modifier.padding(Space.large),
                )
                HorizontalDivider(Modifier.padding(horizontal = Space.large))
                Spacer(Modifier.padding(Space.tight))
                sidebarGroups(model, close).forEachIndexed { index, group ->
                    // A rule between the groups rather than around them, so the
                    // number of rules follows the number of groups on its own.
                    if (index > 0) {
                        HorizontalDivider(Modifier.padding(horizontal = Space.large, vertical = Space.small))
                    }
                    group.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label, fontSize = Type.normal) },
                            icon = { Glyph(entry.ico, tint = LocalContentColor.current, size = IconSizeLarge) },
                            selected = entry.selected,
                            onClick = entry.onPick,
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        },
        content = content,
    )
}

/**
 * The wide presentation: Material's rail, always there. The views at the
 * top, the two other screens pushed to the bottom, which is where a rail
 * keeps the things that leave rather than change.
 */
@Composable
fun SidebarRail(model: SkemaModel, modifier: Modifier = Modifier) {
    val groups = sidebarGroups(model) {}
    NavigationRail(
        modifier = modifier,
        header = {
            Text(
                "Skema",
                color = MaterialTheme.colorScheme.primary,
                fontSize = Type.normal,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(vertical = Space.small),
            )
        },
    ) {
        groups.first().forEach { entry -> RailItem(entry) }
        Spacer(Modifier.weight(1f))
        groups.last().forEach { entry -> RailItem(entry) }
    }
}

@Composable
private fun RailItem(entry: SidebarEntry) {
    NavigationRailItem(
        selected = entry.selected,
        onClick = entry.onPick,
        icon = { Glyph(entry.ico, tint = LocalContentColor.current, size = IconSizeLarge) },
        label = { Text(entry.label, fontSize = Type.small, maxLines = 1) },
    )
}

/** The drawer sheet's trailing corner, Material's extra large. */
private val DRAWER_CORNER = 28.dp
