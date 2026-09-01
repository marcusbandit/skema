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

    ./gradlew :desktopApp:run          # same UI, no emulator
    ./gradlew :androidApp:assembleDebug

Then open Settings and paste your feed URL. It is stored in the app's own
private directory (`~/.local/share/skema` on the desktop, `filesDir` on
Android) and never leaves the device except to ask DTU for the feed.

## What it does

Syncs on open and on demand, keeps the last feed so it opens instantly
offline, and reports what moved since the previous sync. The feed sends no
`ETag` and no `Last-Modified`, only `no-cache, no-store`, so there is nothing
cheap to ask: every sync pulls the whole thing and diffs it locally.

Duplicate entries fold into one, taking the room from the generated booking
and the title from the teacher. Entries DTU generated are all titled
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

## Layout

    shared/       KMP library. Parsing, dedup, storage, network, Compose UI.
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

## Not done yet

Background refresh while the app is closed, and notifications when something
moves. Everything needed for both is already here: `SkemaRepository.refresh`
returns a `Diff`, so a WorkManager job on Android is the missing piece.
