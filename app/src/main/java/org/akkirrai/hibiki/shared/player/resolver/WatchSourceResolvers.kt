package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection

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

data class EffectivePlaybackPreferences(
    val playerName: String?,
    val quality: String?,
)

fun resolvePlaybackPreferences(
    sourceId: String,
    savedSelection: PlaybackSelection?,
    explicitPlayerName: String?,
    explicitQuality: String?,
    allowSavedSelection: Boolean,
): EffectivePlaybackPreferences {
    val saved = savedSelection?.takeIf { allowSavedSelection && it.sourceId == sourceId }
    return EffectivePlaybackPreferences(
        playerName = explicitPlayerName ?: saved?.playerName,
        quality = explicitQuality ?: saved?.quality,
    )
}

fun playerPriority(name: String?): Int = when {
    name.containsPlayerToken("kodik") -> 0
    name.containsPlayerToken("aksor") -> 1
    name.containsPlayerToken("alloha") -> 2
    name.containsPlayerToken("sibnet") -> 3
    name.containsPlayerToken("cvh") -> 4
    name.containsPlayerToken("vk") -> 5
    name.containsPlayerToken("aniboom") -> 6
    else -> 10
}

fun matchesPreferredPlayer(candidatePlayerName: String?, preferredPlayerName: String?): Boolean {
    if (preferredPlayerName.isNullOrBlank()) return false
    val normalizedPreferred = preferredPlayerName.normalizePlayerName()
    val normalizedCandidate = candidatePlayerName.normalizePlayerName()
    return normalizedCandidate == normalizedPreferred ||
        normalizedCandidate.contains(normalizedPreferred) ||
        normalizedPreferred.contains(normalizedCandidate)
}

fun matchesPreferredQuality(candidateQuality: String?, preferredQuality: String?): Boolean =
    !preferredQuality.isNullOrBlank() &&
        candidateQuality?.trim()?.equals(preferredQuality.trim(), ignoreCase = true) == true

data class PlayerSelectionCandidate(
    val index: Int,
    val playerName: String?,
    val quality: String?,
)

fun prioritizePlayerSelection(
    candidates: List<PlayerSelectionCandidate>,
    preferredPlayerName: String?,
    preferredQuality: String?,
): List<Int> = candidates.sortedWith(
    compareBy<PlayerSelectionCandidate> {
        if (matchesPreferredPlayer(it.playerName, preferredPlayerName)) 0 else 1
    }.thenBy {
        if (matchesPreferredQuality(it.quality, preferredQuality)) 0 else 1
    }.thenBy { playerPriority(it.playerName) }
).map(PlayerSelectionCandidate::index)

private fun String?.containsPlayerToken(token: String): Boolean =
    normalizePlayerName().contains(token)

private fun String?.normalizePlayerName(): String = orEmpty().trim().lowercase()
