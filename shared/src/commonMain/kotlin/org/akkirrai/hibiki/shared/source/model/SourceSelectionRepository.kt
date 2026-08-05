package org.akkirrai.hibiki.shared.source

/** Persistent selected-source boundary for shared hosts. */
interface SourceSelectionRepository {
    fun loadSelectedSourceId(): String?

    fun saveSelectedSourceId(sourceId: String)
}
