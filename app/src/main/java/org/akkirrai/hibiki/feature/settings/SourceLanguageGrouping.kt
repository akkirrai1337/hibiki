package org.akkirrai.hibiki.feature.settings

import org.akkirrai.beakokit.api.SourceLanguage
import org.akkirrai.hibiki.core.source.AnimeSourceDescriptor

/** A source belongs to exactly one section: the language it primarily serves. */
internal fun groupSourcesByLanguage(
    sources: List<AnimeSourceDescriptor>,
): Map<SourceLanguage, List<AnimeSourceDescriptor>> =
    sources.groupBy { it.info.primaryLanguage }
