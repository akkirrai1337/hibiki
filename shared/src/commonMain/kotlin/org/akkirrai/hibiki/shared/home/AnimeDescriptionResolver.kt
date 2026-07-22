package org.akkirrai.hibiki.shared.home

import org.akkirrai.hibiki.shared.model.Anime

fun List<Anime>.mergeMissingDescriptions(descriptions: Map<String, String>): List<Anime> = map { anime ->
    if (anime.description.isNullOrBlank()) {
        descriptions[anime.id]?.let { description -> anime.copy(description = description) } ?: anime
    } else {
        anime
    }
}

fun List<Anime>.applyDescriptionUpdates(updates: Map<String, Anime>): List<Anime> {
    var changed = false
    val updatedItems = map { anime ->
        updates[anime.id]?.also { changed = true } ?: anime
    }
    return if (changed) updatedItems else this
}
