package org.akkirrai.hibiki.core.anilist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the rest of the app shows about AniList sync: whether a run is going, what is waiting to be sent, and
 * whether the sign-in needs redoing. It is in memory only - the automatic sync fills it in each launch.
 */
object AniListSyncStatus {
    /** Changes a send would make, with how many of them delete an AniList entry. */
    data class Pending(val changes: Int, val removals: Int)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _needsSignIn = MutableStateFlow(false)
    val needsSignIn: StateFlow<Boolean> = _needsSignIn.asStateFlow()

    /** Set when a notification asks for the send preview to be opened. */
    private val _previewRequested = MutableStateFlow(false)
    val previewRequested: StateFlow<Boolean> = _previewRequested.asStateFlow()

    const val EXTRA_OPEN_PREVIEW = "anilist_open_preview"

    fun setPending(plan: AniListPushPlan?) {
        _pending.value = plan
            ?.takeIf { it.items.isNotEmpty() || it.removals.isNotEmpty() }
            ?.let { Pending(it.items.size + it.removals.size, it.removals.size) }
    }

    fun clearPending() {
        _pending.value = null
    }

    fun setSyncing(value: Boolean) {
        _syncing.value = value
    }

    fun setNeedsSignIn(value: Boolean) {
        _needsSignIn.value = value
    }

    fun requestPreview() {
        _previewRequested.value = true
    }

    fun consumePreviewRequest() {
        _previewRequested.value = false
    }
}
