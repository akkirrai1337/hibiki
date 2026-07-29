package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.SourceCapability
import org.akkirrai.beakokit.source.BuiltInSources

/** iOS source metadata backed by the same BeakoKit catalog as Android. */
internal object IosSourceRegistry {
    val sources: List<AppSourceDescriptor> = listOf(
        BuiltInSources.YUMMY_ANIME_ID,
        BuiltInSources.ANI_LIBERTY_ID,
    ).map { sourceId ->
        val info = BuiltInSources.catalog.require(sourceId)
        AppSourceDescriptor(
            id = info.id.value,
            name = info.name,
            language = info.primaryLanguage.tag,
            iconUrl = info.iconUrl,
            supportsPlayback = SourceCapability.PLAYBACK in info.capabilities,
        )
    }
}
