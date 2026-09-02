package dk.bandit.skema.data

import dk.bandit.skema.ics.CalendarEvent
import dk.bandit.skema.ics.IcsParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the app is showing: parsed events plus where they came from. */
data class Snapshot(
    val events: List<CalendarEvent>,
    val fetchedAtEpochSeconds: Long,
    val bytes: Int,
)

/** What changed between the last fetch and this one. */
data class Diff(val added: Int, val removed: Int, val changed: Int) {
    val isEmpty: Boolean get() = added == 0 && removed == 0 && changed == 0
}

sealed interface Refresh {
    data class Ok(val snapshot: Snapshot, val diff: Diff) : Refresh
    data object NoUrl : Refresh
    data class Failed(val message: String) : Refresh
}

@Serializable
private data class Meta(val fetchedAt: Long = 0, val bytes: Int = 0)

@OptIn(ExperimentalTime::class)
class SkemaRepository(
    private val vault: Vault = vault(),
    private val client: HttpClient = httpClient(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val parser = IcsParser(zone)

    val storageLocation: String get() = vault.location

    fun settings(): SkemaSettings =
        vault.read(SETTINGS_FILE)
            ?.let { runCatching { json.decodeFromString<SkemaSettings>(it) }.getOrNull() }
            ?: SkemaSettings()

    fun saveSettings(settings: SkemaSettings) {
        vault.write(SETTINGS_FILE, json.encodeToString(settings))
    }


    /**
     * Where the user was, kept separately from settings because it is not a
     * preference: it is a fact with a shelf life, and mixing it into the
     * settings file would mean every scroll rewrote the user's choices.
     */
    fun session(): Session? =
        vault.read(SESSION_FILE)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }

    fun saveSession(session: Session) {
        vault.write(SESSION_FILE, json.encodeToString(session))
    }

    fun clearSession() {
        vault.delete(SESSION_FILE)
    }
    /** The last feed we pulled, parsed. Null before the first successful fetch. */
    fun cached(): Snapshot? {
        val raw = vault.read(FEED_FILE) ?: return null
        val meta = vault.read(META_FILE)
            ?.let { runCatching { json.decodeFromString<Meta>(it) }.getOrNull() }
            ?: Meta()
        return Snapshot(parser.parse(raw), meta.fetchedAt, meta.bytes.takeIf { it > 0 } ?: raw.length)
    }

    /**
     * The feed sends no ETag and no Last-Modified, only "no-cache, no-store",
     * so there is nothing to ask about cheaply. Every refresh pulls the whole
     * thing and we work out what moved ourselves.
     */
    suspend fun refresh(url: String): Refresh {
        if (url.isBlank()) return Refresh.NoUrl
        val before = cached()?.events.orEmpty()

        val body = try {
            val response = client.get(url.trim())
            if (!response.status.isSuccess()) {
                return Refresh.Failed("DTU answered " + response.status.value + " " + response.status.description)
            }
            response.bodyAsText()
        } catch (e: Exception) {
            return Refresh.Failed(e.message ?: "Could not reach the feed")
        }

        if (!body.contains("BEGIN:VCALENDAR")) {
            return Refresh.Failed("That link did not return a calendar. Check it is the .ics feed URL.")
        }

        val now = Clock.System.now().epochSeconds
        vault.write(FEED_FILE, body)
        vault.write(META_FILE, json.encodeToString(Meta(now, body.length)))

        val snapshot = Snapshot(parser.parse(body), now, body.length)
        return Refresh.Ok(snapshot, diff(before, snapshot.events))
    }

    fun clearCache() {
        vault.delete(FEED_FILE)
        vault.delete(META_FILE)
    }

    /**
     * A UID plus its occurrence number identifies an entry across fetches;
     * the rest is what a change would move. Comparing fingerprints catches a
     * room swap or a time shift, which is the whole point of resyncing.
     */
    private fun diff(before: List<CalendarEvent>, after: List<CalendarEvent>): Diff {
        if (before.isEmpty()) return Diff(0, 0, 0)
        val a = before.associate { key(it) to fingerprint(it) }
        val b = after.associate { key(it) to fingerprint(it) }
        val added = b.keys.count { it !in a }
        val removed = a.keys.count { it !in b }
        val changed = b.count { (k, v) -> a[k] != null && a[k] != v }
        return Diff(added, removed, changed)
    }

    private fun key(e: CalendarEvent) = e.uid + "#" + e.occurrence

    private fun fingerprint(e: CalendarEvent) =
        listOf(
            e.startEpochSeconds.toString(),
            e.summary,
            e.rooms.joinToString("|"),
            e.end?.toString().orEmpty(),
        ).joinToString(" ")
}
