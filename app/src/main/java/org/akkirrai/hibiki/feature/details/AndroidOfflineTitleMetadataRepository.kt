package org.akkirrai.hibiki.feature.details

import org.akkirrai.hibiki.core.source.OfflineTitleMetadataRepository
import org.akkirrai.hibiki.shared.catalog.model.Anime

/** Android bridge for the existing title metadata cache. */
internal class AndroidOfflineTitleMetadataRepository(
    private val delegate: OfflineTitleMetadataRepository,
) : org.akkirrai.hibiki.shared.details.data.OfflineTitleMetadataRepository {
    override fun get(id: String): Anime? = delegate.get(id)

    override fun save(anime: Anime) {
        delegate.save(anime)
    }
}
