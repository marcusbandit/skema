# skema

A Kotlin Multiplatform calendar for DTU Learn. Paste your calendar link into
Settings and it syncs, caches and shows your timetable. Runs on Android and on
the desktop from the same code.

## Why this exists

DTU Learn's calendar cannot tell you that something changed. It will happily
show you a lecture that moved rooms yesterday without ever mentioning the
move. This reads the same data, keeps it, and can compare today against
yesterday.

## Running it

This is an Android app first, so it is developed against the emulator and
looked at, not left at `assembleDebug`:

    scripts/emulator.sh                # starts the skema_fold AVD, headless
    scripts/run.sh                     # build, install, launch
    scripts/shot.sh                    # screenshot straight off the device
    scripts/fold.sh fold | unfold      # drive the hinge
    scripts/postures.sh <label>        # capture both postures at once

    ./gradlew :desktopApp:run          # same UI in a window, for quick looks

The emulator is headless by default and driven entirely over adb. That is not
a limitation: `screencap` gives exact device pixels, and no window has to
appear on anyone's workspace. `scripts/emulator.sh --window` if you want to
watch it.

The AVD is `skema_fold`, a Pixel Fold profile with a real hinge. Folded it is
411 x 797 dp, unfolded 841 x 701 dp.

Then open Settings and paste your feed URL. It is stored in the app's own
private directory (`~/.local/share/skema` on the desktop, `filesDir` on
Android) and never leaves the device except to ask DTU for the feed.

## What it does

Syncs on open and on demand, keeps the last feed so it opens instantly
offline, and reports what moved since the previous sync. The feed sends no
`ETag` and no `Last-Modified`, only `no-cache, no-store`, so there is nothing
cheap to ask: every sync pulls the whole thing and diffs it locally.

Four views: month, week, day and agenda. Week is the one the timetable is
actually read in, so it is the default. Pinching zooms the hour height, and
pinching past either end of the range walks to the next view along, so month
to week to day is one continuous gesture. Swiping sideways moves a week, a
day or a month depending on what is showing. The grid is the whole 24 hours,
always: a window derived from the events put the same 13:00 at a different
height on different weeks.

Syncing is not a button. It runs when the app opens and every five minutes
after that, which is far finer than the scale a timetable changes on, so
there is nothing to opt out of.

Where you were is restored when you come back, if you were there in the last
half hour. Leaving the app and returning is free, because the state lives in
a `remember` at the root and the activity is not recreated. The disk copy
exists for the case where Android killed the process in between, which is
indistinguishable from a cold start once the memory is gone, so elapsed time
decides. Open it the next morning and it opens on today, as a calendar
should.

Every course gets its own colour, derived rather than picked from a list:
hues are spread evenly over the number of courses present, in OKLCH so the
steps are even to the eye, with an arc reserved around DTU red so no course
can be mistaken for the clash marker. Any subject can be pinned to a hue by
hand in Settings, and the rest re-spread around it. Lightness and chroma are
not adjustable, because they are what hold the text contrast above 4.5:1.

Overlapping events stack rather than share a column. Splitting a column
between them is the textbook answer and it fails on a folded phone: 73dp
split two ways is 36dp a lane, which holds no words, so two events become two
anonymous stripes. Each event now keeps nearly the full width, inset by depth
so the one underneath still shows its rail, with later starts drawn on top so
the earlier event's header sits in the clear. Events starting in the same
minute step down one header each. A pane wide enough to hold words keeps the
honest side by side layout, and the test is on width, so zooming in returns a
clashing day to it.

Duplicate entries merge field by field rather than one winning: where the two
agree the value stands, where they differ the teacher's entry wins, and where
the teacher's entry says nothing the room booking fills in. On one real feed
that keeps the human title on all 86 mergeable slots while 82 of them gain
rooms only the booking knew about. Entries DTU generated are all titled
"Lecture" and name their course only in `LOCATION`, so the course leads and
the generic word goes underneath.

## The parser

`shared/src/commonMain/kotlin/dk/bandit/skema/ics/` parses the DTU Learn
iCalendar feed. It has no Android or JVM dependencies, so it runs anywhere
Kotlin does, and its tests run in milliseconds as plain JVM tests.

    ./gradlew :shared:jvmTest

Run it against a real feed as well as the fixture:

    ./gradlew :shared:jvmTest -Dskema.feed=/path/to/feed.ics --info

Measured on a real 712-entry feed, and cross-checked against an independent
Python implementation that agrees on every number:

| | |
|---|---|
| entries, and events after recurrence expansion | 712, 736 |
| generated from room bookings / hand made | 278 / 458 |
| slots listing the same course twice | 99 |
| genuine conflicts, different courses at once | 13 |
| events with no derivable course code | 18 |
| events left after collapsing duplicates | 603 |

## Getting your feed URL

In DTU Learn: Calendar, Settings, tick Enable Calendar Feeds, Save, then
Subscribe, and choose All Calendars and Tasks.

The URL contains a token that reads your entire calendar with no login.
Treat it as a password. `.gitignore` already excludes `*.ics` and
`feed-url.txt`, and nothing in the repo should ever contain the token.

## Four things about this feed that will break a naive parser

These are not hypothetical. Each one cost real time to find, and each has a
test that fails if it regresses.

**It folds long lines with a TAB, and uses bare LF rather than CRLF.**
RFC 5545 allows a space or a tab; most quick implementations only handle the
space. Unfold on spaces alone and 373 of 712 `LOCATION` values silently lose
their tail mid-string. Nothing throws.

**99 slots contain the same class twice.** DTU generates events from room
bookings *and* teachers create their own, so a lecture appears as both, with
different titles and different room notations. Deduplication is most of the
work, not a finishing touch.

**Two events use `TZID=Europe/Copenhagen` while everything else is UTC.**
Those two are half of a duplicate pair. Treat a `TZID` timestamp as UTC and
the pair lands two hours apart and never looks like a collision.

**`LOCATION` carries the only course attribution there is**, in three
different shapes, and 18 events have none at all. Every UID begins `6606-`,
DTU's org unit, so the UID never tells you the course.

Recurrence, by contrast, is a non-problem: 2 of 712 entries carry an `RRULE`,
both plain weekly, and there is no `EXDATE`, `RDATE`, `EXRULE` or
`RECURRENCE-ID` anywhere. A full RFC 5545 recurrence engine is not needed.

## Semesters and enrolment

DTU writes the term into `LOCATION` as a suffix on the course name, and the
display code was throwing it away as noise, because it repeats on every card.
Across a whole feed it is the strongest signal there is: 694 of 712 entries
carry one, and on this feed they are Fall 2025, Spring 2026, Summer 2026 and
Fall 2026. `ics/Term.kt` reads it, falling back to the date for the 18 that
carry no course at all. Terms sort academically, so Fall 2025 precedes Spring
2026, and a January course belongs to the autumn that opened the year rather
than the calendar year it sits in.

The feed is a record of the university, not of your degree: this one reaches
back to 2019 and 98 of its entries predate the owner enrolling. An enrolment
date drops them, and the date the app suggests is the start of the earliest
semester DTU itself named.

## Folding

The app branches on size in exactly one place, `CalendarShell`. A pane at
least 600dp wide gets two panes, the calendar and a rail holding the selected
day or the tapped event; anything narrower gets one pane and a bottom sheet.
600dp rather than 840dp because the unfolded inner screen is 841 x 701 lying
down and 701 x 841 standing up, and a device should not change layout when
you rotate it.

Views measure the pane they were given, not the window, which is what stops
five day columns being sized for 841dp and drawn into 571dp.

The fold itself is seamless because `AndroidManifest.xml` lists every axis
the hinge moves in `configChanges`. Without it the hinge is an ordinary
configuration change: Android destroys the activity, the app restarts and
refetches, and the scroll position and selected day are gone. With it the
process keeps running and Compose recomposes against new constraints, which
is the same code path as a desktop window resize. Verified by folding a
running app: same pid, no activity relaunch, pixel-identical afterwards.

## Layout

    shared/       KMP library. Parsing, merging, storage, network, Compose UI.
                  Everything except the two lines each platform needs.
    androidApp/   Android application. AGP 9 forbids the application plugin
                  inside a KMP module, so this is deliberately separate.
    desktopApp/   Plain JVM. Same UI, no emulator, fast iteration.

iOS is left out because linking it needs a Mac. `shared/build.gradle.kts`
has the two lines to add it back.

## Toolchain

Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.1.0, Gradle 9.5.0,
kotlinx-datetime 0.8.0, compileSdk 37, minSdk 26. Gradle runs on JDK 21 via
`org.gradle.java.home`; JDK 26 also works but 21 matches what the IDE ships.

## Platform glue, all of it

Two things cannot be written in common code, so each has an `expect` and two
`actual`s in `shared/src/{android,jvm}Main`:

    Vault      where files go. Android needs a Context, so MainActivity calls
               SkemaAndroid.init(applicationContext) before App().
    httpClient Ktor has no common engine. OkHttp on Android, CIO on the JVM.

`FileVault` is deliberately duplicated between the two rather than hoisted
into a custom source-set hierarchy, which would be a lot of build machinery
to save fifteen lines.

## Design rules this follows

Three font sizes for the whole app, from `Type` in `ui/Theme.kt`, with
hierarchy carried by weight, colour and space. No font family is named
anywhere, so every face is the platform's own system sans and nothing is
downloaded. Every rounded corner goes through `SquircleShape`, which is
G2-continuous; `RoundedCornerShape` appears nowhere.

Anything that scales with a count is computed from the count. Day columns,
overlap lanes, hue assignment, month cell dots and hour label density are all
formulas, not tables, so adding the weekend or a course changes a number and
nothing else. `ui/Lanes.kt` is the clearest case: which lane an event sits in
is a property of the set, not the event.

## Not done yet

Background refresh while the app is closed, and notifications when something
moves. Everything needed for both is already here: `SkemaRepository.refresh`
returns a `Diff`, so a WorkManager job on Android is the missing piece.

Hues are spread over every course the feed has ever mentioned, which for a
feed going back to 2019 is fifteen. Only about six matter in any one term, so
the six on screen are further apart in hue than they need to be. Narrowing
the set to the courses in the visible range would give the current term more
separation.
