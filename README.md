# skema

A Kotlin Multiplatform calendar for DTU Learn. Right now it is the parsing
core plus a verified build; the UI is not written yet.

## Why this exists

DTU Learn's calendar cannot tell you that something changed. It will happily
show you a lecture that moved rooms yesterday without ever mentioning the
move. This reads the same data, keeps it, and can compare today against
yesterday.

## What works today

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

    shared/       KMP library. Parsing, dedup, and later the Compose UI.
    androidApp/   Android application. AGP 9 forbids the application plugin
                  inside a KMP module, so this is deliberately separate.
    desktopApp/   Plain JVM. Same UI, no emulator, fast iteration.

iOS is left out because linking it needs a Mac. `shared/build.gradle.kts`
has the two lines to add it back.

## Toolchain

Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.1.0, Gradle 9.5.0,
kotlinx-datetime 0.8.0, compileSdk 37, minSdk 26. Gradle runs on JDK 21 via
`org.gradle.java.home`; JDK 26 also works but 21 matches what the IDE ships.

## Not done yet

Fetching over the network, storage, the UI, and background refresh. The
parser takes a `String`, so nothing above depends on how that string arrives.
