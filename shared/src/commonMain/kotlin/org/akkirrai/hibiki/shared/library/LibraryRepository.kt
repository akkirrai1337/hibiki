package org.akkirrai.hibiki.shared.library

/** Platform-neutral storage boundary for the entries displayed by the shared library screen. */
interface LibraryRepository {
    suspend fun getEntries(): List<LibraryEntry>
}

/** Small deterministic store for hosts that have not connected persistent storage yet. */
class InMemoryLibraryRepository(
    private val initialEntries: List<LibraryEntry> = emptyList(),
) : LibraryRepository {
    override suspend fun getEntries(): List<LibraryEntry> = initialEntries
}
