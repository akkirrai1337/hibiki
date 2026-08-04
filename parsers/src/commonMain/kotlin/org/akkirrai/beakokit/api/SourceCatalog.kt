package org.akkirrai.beakokit.api

fun interface SourceFactory {
    fun create(context: SourceContext): AnimeSource
}

data class SourceCatalogEntry(
    val info: SourceInfo,
    val factory: SourceFactory,
    val registrationOrder: Int? = null,
) {
    fun create(context: SourceContext): AnimeSource {
        val source = factory.create(context)
        check(source.info == info) { "Factory metadata does not match catalog entry: ${info.id}" }
        (source as? ConfigurableSource)?.configSchema?.requireValid(context.config)
        SourceContractValidator.requireValid(source)
        return source
    }
}

/** Immutable source metadata and factory catalog; designed to become KSP-generated later. */
class SourceCatalog(sourceEntries: Iterable<SourceCatalogEntry>) {
    val entries: List<SourceCatalogEntry> = sourceEntries.toList()
    val sources: List<SourceInfo> = entries.map(SourceCatalogEntry::info)
    private val sourcesById = sources.associateBy(SourceInfo::id)
    private val entriesById = entries.associateBy { it.info.id }

    init {
        require(sourcesById.size == sources.size) {
            val duplicates = sources.groupingBy(SourceInfo::id)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            "Duplicate source IDs: ${duplicates.joinToString()}"
        }
        if (entries.all { it.registrationOrder != null }) {
            val orders = entries.mapNotNull(SourceCatalogEntry::registrationOrder)
            require(orders.distinct().size == orders.size) {
                "Duplicate source registration orders: ${orders.groupingBy { it }.eachCount()
                    .filterValues { count -> count > 1 }.keys.joinToString()}"
            }
        }
    }

    operator fun get(id: SourceId): SourceInfo? = sourcesById[id]

    fun require(id: SourceId): SourceInfo = sourcesById[id]
        ?: error("Source is not registered: $id")

    fun create(id: SourceId, context: SourceContext): AnimeSource =
        entriesById[id]?.create(context) ?: error("Source is not registered: $id")

    fun mergedWith(other: SourceCatalog): SourceCatalog {
        val combined = entries + other.entries
        return SourceCatalog(
            if (combined.all { it.registrationOrder != null }) {
                combined.sortedBy(SourceCatalogEntry::registrationOrder)
            } else {
                combined
            },
        )
    }
}
