package org.akkirrai.hibiki.player

/** Shared lock/unlock state for the common player controls. */
data class PlayerLockState(
    val isLocked: Boolean = false,
    val isUnlockButtonVisible: Boolean = false,
) {
    fun lock(): PlayerLockState = copy(
        isLocked = true,
        isUnlockButtonVisible = true,
    )

    fun unlock(): PlayerLockState = PlayerLockState()
}
