package org.akkirrai.hibiki.shared.demo

import org.akkirrai.hibiki.shared.library.LibraryCategory
import org.akkirrai.hibiki.shared.profile.LocalLibraryItem
import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository

/** Deterministic profile data used by the development prototype. */
object DemoLocalProfileDataRepository : LocalProfileDataRepository {
    override suspend fun load(): LocalProfileData = LocalProfileData(
        profileName = "hibiki",
        library = DemoCatalog.items.map { anime ->
            LocalLibraryItem(
                id = anime.id,
                anime = anime,
                categories = setOf(LibraryCategory.Planned),
                addedAt = null,
            )
        },
    )
}
