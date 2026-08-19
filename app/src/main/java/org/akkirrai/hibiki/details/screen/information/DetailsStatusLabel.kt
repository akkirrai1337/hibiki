package org.akkirrai.hibiki.details.screen

import org.akkirrai.hibiki.details.model.AnimeReleaseStatus
import org.akkirrai.hibiki.details.model.parseAnimeReleaseStatus

fun resolveDetailsStatusLabel(
    status: String,
    ongoingLabel: String,
    releasedLabel: String,
    announcementLabel: String,
): String = when (parseAnimeReleaseStatus(status)) {
    AnimeReleaseStatus.Ongoing -> ongoingLabel
    AnimeReleaseStatus.Released -> releasedLabel
    AnimeReleaseStatus.Announcement -> announcementLabel
    null -> status
}
