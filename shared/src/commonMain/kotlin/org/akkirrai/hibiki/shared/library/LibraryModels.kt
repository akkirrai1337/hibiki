package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.model.Anime

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
    Saved("saved");

    companion object {
        fun fromStorageValue(value: String): LibraryCategory? =
            entries.firstOrNull { it.storageValue == value }
    }
}
