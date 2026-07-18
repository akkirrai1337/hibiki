package org.akkirrai.beakokit.api

/** Immutable metadata catalog. Source factories will be added when the first source is migrated. */
class SourceCatalog(entries: Iterable<SourceInfo>) {
    val sources: List<SourceInfo> = entries.toList()
    private val sourcesById = sources.associateBy(SourceInfo::id)

    init {
        require(sourcesById.size == sources.size) {
            val duplicates = sources.groupingBy(SourceInfo::id)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            "Duplicate source IDs: ${duplicates.joinToString()}"
        }
    }

    operator fun get(id: SourceId): SourceInfo? = sourcesById[id]

    fun require(id: SourceId): SourceInfo = sourcesById[id]
        ?: error("Source is not registered: $id")
}
