package dk.bandit.skema.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.bandit.skema.data.ViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The chrome around whichever calendar is showing, and the one place that
 * decides how the screen is divided.
 *
 * There is exactly one branch on size in the whole app and it is here. A wide
 * frame gets two panes, a narrow one gets a single pane with a sheet. Both
 * read the same model, so unfolding the phone moves the detail from a sheet
 * into a rail and changes nothing else: same day, same zoom, same selection,
 * no reload. That is what makes the fold seamless rather than a restart.
 */
@Composable
fun CalendarShell(model: SkemaModel) {
    val dark = skema.isDark
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val frame = Frame(maxWidth, maxHeight)

        // Rebuilt only when the course set, the pinned hues or the theme
        // change. The hue assignment re-spreads every course, so doing this
        // per frame would be visible work for no reason.
        val palette = remember(model.courseCodes, model.settings.courseHues, dark) {
            CoursePalette(model.courseCodes, model.settings.courseHues, dark)
        }

        CompositionLocalProvider(LocalFrame provides frame) {
            if (frame.dual) DualPane(model, palette, frame) else SinglePane(model, palette)
        }
    }
}

@Composable
private fun DualPane(model: SkemaModel, palette: CoursePalette, frame: Frame) {
    val c = skema
    // A third of the width, floored so the rail can hold a room list and
    // capped so the week never loses the space it actually needs.
    val rail = (frame.width * 0.32f).coerceIn(260.dp, 380.dp)

    // The top bar lives in the LEFT PANE, not the window, so what fits in it
    // is the pane's width minus the rail. Unfolded that is about 571dp, and a
    // four way switcher plus two buttons leaves the month name roughly 60dp,
    // which is how "Aug to Sep" became "Aug to". Below this the switcher goes
    // back to the bottom where it has a row of its own.
    val pane = frame.width - rail - 1.dp
    val inline = pane >= 640.dp

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(model, inlineSwitcher = inline)
            Calendar(model, palette, Modifier.weight(1f))
            if (!inline) ViewSwitcher(model, Modifier.fillMaxWidth().padding(Space.small))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
        Box(Modifier.width(rail).fillMaxHeight().background(c.surface)) {
            val picked = model.picked
            if (picked != null) {
                EventDetail(
                    event = picked,
                    tint = palette.tint(picked.courseCode),
                    conflict = picked.startEpochSeconds in model.conflicts,
                    onClose = { model.picked = null },
                )
            } else {
                DaySummary(model, palette)
            }
        }
    }
}

@Composable
private fun SinglePane(model: SkemaModel, palette: CoursePalette) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(model, inlineSwitcher = false)
            Calendar(model, palette, Modifier.weight(1f))
            // The switcher sits at the bottom on a phone because that is
            // where a thumb is, and folded this app is a tall phone.
            ViewSwitcher(model, Modifier.fillMaxWidth().padding(Space.small))
        }
        model.picked?.let { picked ->
            DetailSheet(
                event = picked,
                tint = palette.tint(picked.courseCode),
                conflict = picked.startEpochSeconds in model.conflicts,
                onClose = { model.picked = null },
            )
        }
    }
}

/**
 * Switching view animates along the zoom axis it represents: going to a
 * narrower view grows, going wider shrinks. The motion tells you which way
 * you moved, which matters when the gesture that got you here was a pinch
 * that could have gone either way.
 */
@Composable
private fun Calendar(model: SkemaModel, palette: CoursePalette, modifier: Modifier = Modifier) {
    // Re-measure here rather than reusing the window frame.
    //
    // The shell decides one pane or two from the size of the WINDOW, but a
    // view then has to lay its columns out inside the pane it was actually
    // given, which on a two pane screen is about two thirds of that. Reading
    // the window frame down here sizes five day columns for 841dp and then
    // draws them into 571dp, and Friday goes off the edge.
    BoxWithConstraints(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFrame provides Frame(maxWidth, maxHeight)) {
            CalendarBody(model, palette)
        }
    }
}

@Composable
private fun CalendarBody(model: SkemaModel, palette: CoursePalette) {
    AnimatedContent(
        targetState = model.view,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val deeper = targetState.ordinal > initialState.ordinal
            val grow = if (deeper) 0.94f else 1.06f
            val shrink = if (deeper) 1.06f else 0.94f
            (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = grow)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(220), targetScale = shrink))
        },
        label = "view",
    ) { view ->
        when (view) {
            ViewMode.Month -> MonthView(model, palette, Modifier.fillMaxSize())
            ViewMode.Week -> WeekView(model, palette, Modifier.fillMaxSize())
            ViewMode.Day -> DayView(model, palette, Modifier.fillMaxSize())
            ViewMode.Agenda -> AgendaView(model, palette, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TopBar(model: SkemaModel, inlineSwitcher: Boolean) {
    val c = skema
    val scope = rememberCoroutineScope()
    val onToday = model.anchor == today()

    Column(Modifier.fillMaxWidth().padding(horizontal = Space.medium, vertical = Space.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    headline(model),
                    color = c.ink,
                    fontSize = Type.large,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(subhead(model), color = c.inkMuted, fontSize = Type.small, maxLines = 1)
            }

            if (inlineSwitcher) {
                ViewSwitcher(model, Modifier.width(280.dp))
                Spacer(Modifier.width(Space.small))
            }
            if (!onToday) {
                PillButton("Today", { model.goToday() })
                Spacer(Modifier.width(Space.tight))
            }
            PillButton(
                label = if (model.busy) "Syncing" else "Sync",
                onClick = { scope.launch { model.refresh() } },
                enabled = !model.busy && model.hasUrl,
            )
            Spacer(Modifier.width(Space.tight))
            PillButton("Settings", { model.screen = Screen.Settings })
        }

        // "Nothing moved, 736 entries" is worth saying once and then not
        // worth a permanent line of a 700dp tall screen. A failure stays put,
        // because that one you have to act on.
        if (model.status.isNotBlank()) {
            if (!model.failed && !model.busy) {
                LaunchedEffect(model.status) {
                    delay(6_000)
                    model.clearStatus()
                }
            }
            Spacer(Modifier.height(Space.unit))
            Text(
                model.status,
                color = if (model.failed) c.accentStrong else c.inkMuted,
                fontSize = Type.small,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ViewSwitcher(model: SkemaModel, modifier: Modifier = Modifier) {
    Segmented(
        options = ViewMode.entries,
        selected = model.view,
        label = { it.name },
        onSelect = { model.setView(it) },
        modifier = modifier,
    )
}

/**
 * The sheet on a narrow frame. Dragged down to dismiss, because reaching a
 * close button at the top of a sheet on a 797dp tall phone means moving your
 * whole hand.
 */
@Composable
private fun DetailSheet(
    event: dk.bandit.skema.ics.CalendarEvent,
    tint: CourseTint,
    conflict: Boolean,
    onClose: () -> Unit,
) {
    val c = skema
    var drag by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.ink.copy(alpha = 0.32f))
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(SquircleShape(Radius.sheet))
                .background(c.surface)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > 120f) onClose(); drag = 0f },
                        onVerticalDrag = { change, dy -> drag += dy; change.consume() },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = Space.small)
                    .width(36.dp)
                    .height(4.dp)
                    .background(c.hairline, pillShape()),
            )
            EventDetail(event, tint, conflict, onClose)
        }
    }
}

/** ISO week number: the week owning this week's Thursday, which is the definition. */
fun isoWeek(d: LocalDate): Int {
    val thursday = d.shift(3 - d.dayOfWeek.ordinal)
    return (thursday.dayOfYear - 1) / 7 + 1
}

/**
 * A teaching week straddles a month boundary about once a month, and calling
 * the week of 31 August to 4 September "Aug" is wrong for four of its five
 * days. So the label is built from the days actually on screen and names both
 * months when there are two. The year moves to the subhead: it changes once a
 * year and it was the first thing to be truncated in the narrow pane.
 */
private fun weekLabel(model: SkemaModel): String {
    val from = weekStart(model.anchor)
    val to = from.shift(Grid.dayCount(model.settings.showWeekend) - 1)
    val first = monthShort(from.month)
    val last = monthShort(to.month)
    return if (first == last) first else "$first to $last"
}

private fun headline(model: SkemaModel): String = when (model.view) {
    ViewMode.Month -> monthShort(model.anchor.month)
    ViewMode.Week -> weekLabel(model)
    ViewMode.Day -> relativeDay(model.selected) ?: dateLabel(model.selected)
    ViewMode.Agenda -> "Agenda"
}

private fun subhead(model: SkemaModel): String = when (model.view) {
    ViewMode.Month -> model.anchor.year.toString()
    ViewMode.Week -> "Week " + isoWeek(model.anchor) + ", " + weekStart(model.anchor).year
    ViewMode.Day -> "Week " + isoWeek(model.selected) + ", " + model.selected.year
    ViewMode.Agenda -> "Everything ahead"
}
