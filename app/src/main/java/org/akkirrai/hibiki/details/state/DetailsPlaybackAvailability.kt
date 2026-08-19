package org.akkirrai.hibiki.details.state

import org.akkirrai.hibiki.details.model.isAnnouncementStatus

import org.akkirrai.hibiki.core.source.AppSourceDescriptor

fun resolveDetailsPlaybackAvailability(
    supportsPlayback: Boolean,
    status: String,
    episodesLabel: String,
): Boolean = supportsPlayback && !isAnnouncementStatus(status, episodesLabel)

fun resolveDetailsPlaybackAvailability(
    watchRepositoryAvailable: Boolean,
    sources: List<AppSourceDescriptor>,
    selectedSourceId: String?,
    status: String,
    episodesLabel: String,
): Boolean {
    val supportsPlayback = sources
        .firstOrNull { it.id == selectedSourceId }
        ?.supportsPlayback
        ?: false
    return watchRepositoryAvailable && resolveDetailsPlaybackAvailability(
        supportsPlayback = supportsPlayback,
        status = status,
        episodesLabel = episodesLabel,
    )
}
