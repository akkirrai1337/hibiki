package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.model.PlayerLink

fun selectPlaybackLinks(
    links: List<PlayerLink>,
    supports: (PlayerLink) -> Boolean,
    preferredPlayerName: String? = null,
    preferredQuality: String? = null,
): List<PlayerLink> {
    val supportedLinks = links.filter(supports)
    return prioritizePlayerSelection(
        candidates = supportedLinks.mapIndexed { index, link ->
            PlayerSelectionCandidate(index, link.playerName, link.quality)
        },
        preferredPlayerName = preferredPlayerName,
        preferredQuality = preferredQuality,
    ).map(supportedLinks::get)
}
