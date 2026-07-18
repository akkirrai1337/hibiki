package org.akkirrai.hibiki.core.source

import org.akkirrai.beakokit.api.AnimeKey

internal object YummyIdMigration {
    fun normalizeTitleId(rawId: String): String {
        val trimmed = rawId.trim()
        if (trimmed.isBlank()) return trimmed
        AnimeKey.parse(trimmed)?.let { return it.value }
        if (trimmed.all(Char::isDigit)) return trimmed

        if (!trimmed.contains('|') && !trimmed.contains("primary=")) {
            return trimmed
        }

        val parts = trimmed.split('|')
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                entry.substring(0, separatorIndex) to entry.substring(separatorIndex + 1)
            }
            .toMap()

        val primaryRaw = parts["primary"].orEmpty()
        val primaryId = primaryRaw.substringAfter(':', missingDelimiterValue = trimmed)

        return parts["yummy"]
            ?.takeIf { it.all(Char::isDigit) }
            ?: primaryId.takeIf { primaryRaw.startsWith("yummy:") && it.all(Char::isDigit) }
            ?: primaryId
    }

    /** Canonical ID first, followed by identifiers written by older Hibiki versions. */
    fun compatibleTitleIds(rawId: String): List<String> {
        val trimmed = rawId.trim()
        val normalized = normalizeTitleId(trimmed)
        val scoped = AnimeKey.parse(trimmed) ?: AnimeKey.parse(normalized)
        return buildList {
            add(normalized)
            if (trimmed.isNotBlank()) add(trimmed)
            scoped?.let { key ->
                add(
                    "source:${key.sourceId.value.uppercase().replace('-', '_')}:${key.nativeId}",
                )
            }
        }.distinct()
    }
}
