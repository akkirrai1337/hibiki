package org.akkirrai.hibiki.shared.prototype

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.profile.LocalLibraryItem
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository

/** Deterministic profile data used by the development prototype. */
object PrototypeLocalProfileDataRepository : LocalProfileDataRepository {
    override suspend fun load(): LocalProfileData = LocalProfileData(
        profileName = "hibiki",
        library = PrototypeCatalog.items.map { anime ->
            LocalLibraryItem(
                id = anime.id,
                anime = anime,
                categories = setOf(LibraryCategory.Planned),
                addedAt = null,
            )
        },
    )
}
