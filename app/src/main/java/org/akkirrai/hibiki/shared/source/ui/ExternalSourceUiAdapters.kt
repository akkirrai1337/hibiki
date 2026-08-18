package org.akkirrai.hibiki.shared.source

import org.akkirrai.beakokit.api.ExternalSourceRegistry
import org.akkirrai.beakokit.api.SourceCapability

/** Converts installed external source metadata into the shared source-list contract. */
fun ExternalSourceRegistry.toAppSourceDescriptors(): List<AppSourceDescriptor> =
    sources.map { info ->
        AppSourceDescriptor(
            id = info.id.value,
            name = info.name,
            language = info.primaryLanguage.toString(),
            languageTags = info.languages.mapTo(linkedSetOf()) { it.tag },
            iconUrl = info.iconUrl,
            supportsPlayback = SourceCapability.PLAYBACK in info.capabilities,
            // Search is part of the minimal external runtime contract, not an optional capability.
            supportsSearch = true,
            configSchema = info.configSchema,
        )
    }

/** Merges source metadata; an installed external package replaces its built-in counterpart. */
fun mergeAppSourceDescriptors(
    builtIn: List<AppSourceDescriptor>,
    external: List<AppSourceDescriptor>,
): List<AppSourceDescriptor> = (external + builtIn).distinctBy(AppSourceDescriptor::id)
