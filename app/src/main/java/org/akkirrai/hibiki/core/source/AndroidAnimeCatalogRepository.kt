package org.akkirrai.hibiki.core.source

import android.content.Context
import io.ktor.client.HttpClient
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.model.Anime

/** Android data-source adapter for the platform-neutral catalog contract. */
class AndroidAnimeCatalogRepository(
    context: Context,
    client: HttpClient? = null,
) : AnimeCatalogRepository {
    private val delegate = if (client == null) {
        AnimeSearchRepository(context.applicationContext)
    } else {
        AnimeSearchRepository(context.applicationContext, client)
    }

    override val initialItems: List<Anime> = emptyList()

    override suspend fun search(query: String): List<Anime> = delegate.search(query)

    fun close() = delegate.close()
}
