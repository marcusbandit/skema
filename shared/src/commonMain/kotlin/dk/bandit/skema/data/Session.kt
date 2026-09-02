package dk.bandit.skema.data

import kotlinx.serialization.Serializable

/**
 * Where you were last time, so coming back puts you back.
 *
 * The owner's rule: "if I fully close the app and then I open the app again
 * then it shouldn't remember where I am, but if I just quickly go out of it
 * and then back into it it should remember."
 *
 * Two different mechanisms serve that one sentence. Leaving the app and
 * returning while Android keeps the process is handled for free, because the
 * state lives in a `remember` at the root and the activity is not recreated:
 * `configChanges` already covers every axis a fold or a rotate moves. What
 * this file covers is the case where the process was killed in between, which
 * is common on a phone with a browser and a game behind you, and which is
 * indistinguishable from a cold start once the memory is gone.
 *
 * So the disk carries a timestamp and freshness decides. Anything that
 * cannot tell a deliberate close from a reclaim has to pick a proxy, and
 * elapsed time is the proxy the owner themselves reached for. Half an hour is
 * long enough to cover stepping out to answer a message, and short enough
 * that opening the app the next morning starts where a calendar should, which
 * is today.
 */
@Serializable
data class Session(
    val view: ViewMode,
    val anchorEpochDay: Long,
    val selectedEpochDay: Long,
    val hourHeight: Float,
    val savedAtEpochSeconds: Long,
) {
    fun isFreshAt(nowEpochSeconds: Long): Boolean =
        nowEpochSeconds - savedAtEpochSeconds in 0..RESUME_WINDOW_SECONDS

    companion object {
        const val RESUME_WINDOW_SECONDS: Long = 30 * 60
    }
}
