package org.akkirrai.beakokit.api

/** Registry contract shared by built-in and installed external sources. */
interface SourceRegistry {
    val catalog: SourceCatalog

    val sources: List<SourceInfo>
        get() = catalog.sources

    fun requireInfo(id: SourceId): SourceInfo = catalog.require(id)

    fun create(id: SourceId, context: SourceContext): AnimeSource = catalog.create(id, context)
}

/** Registry backed by a fixed set of source factories. */
open class CatalogSourceRegistry(
    override val catalog: SourceCatalog,
) : SourceRegistry

/** Built-in sources retained as a separate registry during migration. */
class BuiltInSourceRegistry(catalog: SourceCatalog) : CatalogSourceRegistry(catalog)

/** Sources installed from external packages. */
class ExternalSourceRegistry(catalog: SourceCatalog) : CatalogSourceRegistry(catalog)

/** Transitional registry exposing both built-in and external sources uniformly. */
class CombinedSourceRegistry(
    builtIn: SourceRegistry,
    external: SourceRegistry,
) : CatalogSourceRegistry(builtIn.catalog.mergedWith(external.catalog))

