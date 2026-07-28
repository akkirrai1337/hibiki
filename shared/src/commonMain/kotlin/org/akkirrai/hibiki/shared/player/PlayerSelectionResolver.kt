package org.akkirrai.hibiki.shared.player

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

private fun String?.containsPlayerToken(token: String): Boolean =
    normalizePlayerName().contains(token)

private fun String?.normalizePlayerName(): String = orEmpty().trim().lowercase()
