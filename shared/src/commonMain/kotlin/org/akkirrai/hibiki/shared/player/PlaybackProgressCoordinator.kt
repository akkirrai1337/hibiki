package org.akkirrai.hibiki.shared.player

/** Coordinates progress writes shared by close, background and host-dispose callbacks. */
class PlaybackProgressCoordinator(
    private val persist: (PlaybackProgressSnapshot) -> Unit,
) {
    private var lastPersisted: PlaybackProgressSnapshot? = null

    fun persistIfChanged(snapshot: PlaybackProgressSnapshot) {
        if (snapshot == lastPersisted) return
        persist(snapshot)
        lastPersisted = snapshot
    }
}
