package org.akkirrai.hibiki.shared.details

fun resolveDetailsPlaybackAvailability(
    supportsPlayback: Boolean,
    status: String,
    episodesLabel: String,
): Boolean = supportsPlayback && !isAnnouncementStatus(status, episodesLabel)
