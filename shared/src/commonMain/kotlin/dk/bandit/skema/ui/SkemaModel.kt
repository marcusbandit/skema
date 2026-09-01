package dk.bandit.skema.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dk.bandit.skema.data.Diff
import dk.bandit.skema.data.Refresh
import dk.bandit.skema.data.SkemaRepository
import dk.bandit.skema.data.SkemaSettings
import dk.bandit.skema.data.Snapshot
import dk.bandit.skema.ics.CalendarEvent

enum class Screen { Agenda, Settings }

/**
 * All the app's state in one object, held by remember{} at the root. No
 * ViewModel, because that is an Android class and this has to run on the
 * desktop too.
 */
class SkemaModel(private val repo: SkemaRepository) {

    var screen by mutableStateOf(Screen.Agenda)

    var settings by mutableStateOf(repo.settings())
        private set

    var events by mutableStateOf<List<CalendarEvent>>(emptyList())
        private set

    var fetchedAt by mutableStateOf<Long?>(null)
        private set

    var feedBytes by mutableStateOf(0)
        private set

    var busy by mutableStateOf(false)
        private set

    var status by mutableStateOf("")
        private set

    var failed by mutableStateOf(false)
        private set

    val storageLocation: String get() = repo.storageLocation

    val hasUrl: Boolean get() = settings.feedUrl.isNotBlank()

    val courseCount: Int get() = events.mapNotNull { it.courseCode }.distinct().size

    fun loadCache() {
        repo.cached()?.let(::adopt)
    }

    fun update(change: (SkemaSettings) -> SkemaSettings) {
        settings = change(settings)
        repo.saveSettings(settings)
    }

    suspend fun refresh() {
        if (busy) return
        busy = true
        failed = false
        status = "Fetching"
        when (val result = repo.refresh(settings.feedUrl)) {
            is Refresh.NoUrl -> {
                status = "No feed link yet"
                failed = true
            }
            is Refresh.Failed -> {
                status = result.message
                failed = true
            }
            is Refresh.Ok -> {
                adopt(result.snapshot)
                status = describe(result.diff, result.snapshot.events.size)
            }
        }
        busy = false
    }

    fun forget() {
        repo.clearCache()
        events = emptyList()
        fetchedAt = null
        feedBytes = 0
        status = ""
        failed = false
    }

    private fun adopt(snapshot: Snapshot) {
        events = snapshot.events
        fetchedAt = snapshot.fetchedAtEpochSeconds
        feedBytes = snapshot.bytes
    }

    private fun describe(diff: Diff, total: Int): String {
        if (diff.isEmpty) return "Nothing moved, $total entries"
        val parts = buildList {
            if (diff.added > 0) add("${diff.added} new")
            if (diff.changed > 0) add("${diff.changed} moved")
            if (diff.removed > 0) add("${diff.removed} gone")
        }
        return parts.joinToString(", ")
    }
}
