package org.akkirrai.hibiki.shared.player

import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.hibiki.shared.player.model.PlaybackSelection
import org.akkirrai.hibiki.shared.player.model.WatchSource
import org.akkirrai.hibiki.shared.player.model.WatchSourceSelection

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

fun resolveWatchSource(
    sources: List<WatchSource>,
    selection: WatchSourceSelection,
): WatchSource? {
    if (sources.isEmpty()) return null
    return if (selection.autoSelect) {
        sources.first()
    } else {
        sources.firstOrNull { it.sourceId == selection.sourceId } ?: sources.first()
    }
}

fun hasWatchSource(selectedSource: WatchSource?, selection: WatchSourceSelection): Boolean =
    selectedSource != null || !selection.sourceTitle.isNullOrBlank()

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

fun resolvePlayerAttemptTimeoutMillis(
    preferredPlayerName: String?,
    candidatePlayerName: String?,
    preferredTimeoutMs: Long,
    automaticTimeoutMs: Long,
): Long = if (matchesPreferredPlayer(candidatePlayerName, preferredPlayerName)) {
    preferredTimeoutMs
} else {
    automaticTimeoutMs
}

private fun String?.containsPlayerToken(token: String): Boolean =
    normalizePlayerName().contains(token)

private fun String?.normalizePlayerName(): String = orEmpty().trim().lowercase()
