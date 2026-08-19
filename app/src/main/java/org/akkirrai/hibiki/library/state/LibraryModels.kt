package org.akkirrai.hibiki.library

import org.akkirrai.hibiki.library.*

import org.akkirrai.hibiki.catalog.model.Anime

data class LibraryEntry(
    val anime: Anime,
    val category: LibraryCategory,
    val addedAt: Long? = null,
)

enum class LibraryCategory(val storageValue: String) {
    Watching("watching"),
    Planned("planned"),
    Completed("completed"),
    Dropped("dropped"),
    OnHold("on_hold"),
    Favorite("favorite"),
    Saved("saved"),
    // Hidden bookkeeping flag, set automatically the moment playback starts. Never a title's
    // "real" category, never shown as a library tab, and never overwrites Watching/Planned/etc.
    // Exists purely to drive Home's continue-watching without visibly cluttering the library.
    Recent("recent");

    companion object {
        fun fromStorageValue(value: String): LibraryCategory? =
            entries.firstOrNull { it.storageValue == value }
    }
}
