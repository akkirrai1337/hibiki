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
        if (source.info != info) {
            throw SourceContractException(
                listOf("Factory metadata does not match catalog entry: ${info.id}"),
            )
        }
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
        val hasRegistrationOrders = entries.any { it.registrationOrder != null }
        if (hasRegistrationOrders) {
            require(entries.all { it.registrationOrder != null }) {
                "Source registration orders must be provided for every catalog entry or none"
            }
            val orders = entries.mapNotNull(SourceCatalogEntry::registrationOrder)
            require(orders.distinct().size == orders.size) {
                "Duplicate source registration orders: ${orders.groupingBy { it }.eachCount()
                    .filterValues { count -> count > 1 }.keys.joinToString()}"
            }
        }
    }

    operator fun get(id: SourceId): SourceInfo? = sourcesById[id]

    fun require(id: SourceId): SourceInfo = sourcesById[id]
        ?: throw SourceNotRegisteredException(id)

    fun create(id: SourceId, context: SourceContext): AnimeSource =
        entriesById[id]?.create(context) ?: throw SourceNotRegisteredException(id)

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

class SourceNotRegisteredException(
    val sourceId: SourceId,
) : SourceException(
    message = "Source is not registered: $sourceId",
    kind = SourceErrorKind.NOT_FOUND,
    code = SourceErrorCode.NOT_FOUND,
)
