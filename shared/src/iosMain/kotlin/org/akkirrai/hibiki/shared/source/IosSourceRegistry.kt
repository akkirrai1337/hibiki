package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.beakokit.source.BuiltInSources

/** iOS source metadata backed by the same BeakoKit catalog as Android. */
internal object IosSourceRegistry {
    private val supportedSources = listOf(
        BuiltInSources.YUMMY_ANIME_ID,
        BuiltInSources.ANI_LIBERTY_ID,
    )

    val sources: List<AppSourceDescriptor> = supportedSources.map { sourceId ->
        val info = BuiltInSources.catalog.require(sourceId)
        AppSourceDescriptor(
            id = info.id.value,
            name = info.name,
            language = info.primaryLanguage.tag,
            languageTags = info.languages.mapTo(linkedSetOf()) { it.tag },
            iconUrl = info.iconUrl,
            supportsPlayback = SourceCapability.PLAYBACK in info.capabilities,
            supportsSearch = true,
        )
    } + listOf(
        AppSourceDescriptor(
            id = "animego",
            name = "AnimeGo",
            language = SourceLanguage.RUSSIAN.tag,
            languageTags = setOf(SourceLanguage.RUSSIAN.tag),
            iconUrl = "https://animego.me/favicon.ico",
            isAvailable = false,
        ),
        AppSourceDescriptor(
            id = "animevost",
            name = "AnimeVost",
            language = SourceLanguage.RUSSIAN.tag,
            languageTags = setOf(SourceLanguage.RUSSIAN.tag),
            iconUrl = "https://animevost.org/favicon.ico",
            isAvailable = false,
        ),
        AppSourceDescriptor(
            id = "animepahe",
            name = "AnimePahe",
            language = SourceLanguage.ENGLISH.tag,
            languageTags = setOf(SourceLanguage.ENGLISH.tag),
            iconUrl = "https://animepahetv.to/favicon.ico",
            isAvailable = false,
        ),
        AppSourceDescriptor(
            id = "gogoanime",
            name = "GogoAnime",
            language = SourceLanguage.ENGLISH.tag,
            languageTags = setOf(SourceLanguage.ENGLISH.tag),
            iconUrl = "https://www.gogoanime.is/favicon.ico",
            isAvailable = false,
        ),
    )
}
