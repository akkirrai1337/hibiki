package org.akkirrai.hibiki.shared.catalog.model

enum class AnimeStatus {
    Finished,
    Releasing,
    NotYetReleased,
    Cancelled,
    Hiatus,
;

    companion object {
        fun fromAlias(alias: String): AnimeStatus = when (alias.trim().lowercase()) {
            "released", "finished", "completed" -> Finished
            "ongoing", "releasing", "airing" -> Releasing
            "announced", "not_yet_released", "not-yet-released" -> NotYetReleased
            "cancelled", "canceled" -> Cancelled
            "hiatus", "paused" -> Hiatus
            else -> Finished
        }
    }
}
