package dk.bandit.skema.ics

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Every conversion between an absolute instant and local wall time goes
 * through here. `Instant` moved from kotlinx-datetime into the Kotlin
 * standard library (`kotlin.time.Instant`), so keeping the references in one
 * file means a future move is a one-line fix rather than a hunt.
 */
internal fun localAt(epochSeconds: Long, zone: TimeZone): LocalDateTime =
    Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
