package org.akkirrai.hibiki.core.metadata

import org.akkirrai.beakokit.metadata.ExternalMetadata
import org.akkirrai.beakokit.metadata.MetadataProviderId
import org.akkirrai.hibiki.core.model.Anime
import org.akkirrai.hibiki.core.model.AnimeRating

/**
 * A provider entry, dressed as the app's own [Anime] so an aggregator-driven catalog can reuse every
 * grid, card and paging path the source catalog already has.
 *
 * The id is what makes that safe: an entry is not a title of any source and cannot be opened like
 * one, so it carries a scheme no source id can collide with (a source id is a lowercase slug and a
 * native id, never this). Anything that receives one has to resolve it first - see
 * [decodeExternalEntryId] and the resolution the catalog screen does on a click.
 */
private const val ENTRY_ID_SCHEME = "hibiki-entry"

fun externalEntryId(provider: MetadataProviderId, externalId: Int): String =
    "$ENTRY_ID_SCHEME:${provider.id}:$externalId"

/** The provider entry an id names, or null when the id is an ordinary source title. */
fun decodeExternalEntryId(id: String): Pair<MetadataProviderId, Int>? {
    val parts = id.split(':')
    if (parts.size != 3 || parts[0] != ENTRY_ID_SCHEME) return null
    val provider = MetadataProviderId.fromId(parts[1]) ?: return null
    val externalId = parts[2].toIntOrNull() ?: return null
    return provider to externalId
}

/**
 * The card an entry draws as.
 *
 * Deliberately not every field: episode counts and statuses read as promises about what is playable,
 * and until this entry has been resolved to a source title nothing here can keep such a promise.
 */
fun ExternalMetadata.toCatalogAnime(preferEnglish: Boolean): Anime {
    val title = when {
        preferEnglish -> englishName ?: romajiName ?: nativeName
        else -> romajiName ?: englishName ?: nativeName
    } ?: "#$externalId"
    return Anime(
        id = externalEntryId(provider, externalId),
        title = title,
        subtitle = listOfNotNull(year?.toString(), type?.uppercase()).joinToString(" · "),
        // Blank rather than invented: an entry says nothing about what a source has uploaded, and a
        // count here would read as a promise this card cannot keep until it is resolved.
        episodesLabel = "",
        status = status.orEmpty(),
        posterUrl = posterUrl,
        description = description,
        genres = genres,
        ratings = score?.let { listOf(AnimeRating(source = provider.ratingSource, value = it, votes = scoreVotes)) }.orEmpty(),
    )
}
