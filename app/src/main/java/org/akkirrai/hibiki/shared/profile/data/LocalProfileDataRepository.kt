package org.akkirrai.hibiki.shared.profile

import org.akkirrai.hibiki.shared.library.LibraryEntry

/** Platform-neutral read boundary for the local profile feature. */
interface LocalProfileDataRepository {
    /**
     * [libraryEntries], when supplied, is reused instead of re-querying library storage —
     * callers that already fetched the library for another screen should pass it through so
     * the same data isn't read twice per refresh.
     */
    suspend fun load(libraryEntries: List<LibraryEntry>? = null): LocalProfileData

    fun updateProfileName(name: String): String? = null

    fun updateProfileAvatar(uri: String) = Unit
}
