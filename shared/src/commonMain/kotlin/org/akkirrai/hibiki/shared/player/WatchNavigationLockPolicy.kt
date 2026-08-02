package org.akkirrai.hibiki.shared.player

/** Re-enables watch-flow actions when the common route advances to the next screen. */
fun watchNavigationLockKey(
    animeId: String?,
    sourceId: String?,
    isPlayerRoute: Boolean,
): String? = when {
    animeId == null -> null
    isPlayerRoute -> "player:$animeId:${sourceId.orEmpty()}"
    sourceId == null -> "sources:$animeId"
    else -> "episodes:$animeId:$sourceId"
}
