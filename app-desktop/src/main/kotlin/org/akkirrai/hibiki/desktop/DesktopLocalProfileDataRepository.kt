package org.akkirrai.hibiki.desktop

import org.akkirrai.hibiki.shared.profile.LocalProfileData
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository

internal class DesktopLocalProfileDataRepository(
    private val progressRepository: DesktopPlaybackProgressRepository,
) : LocalProfileDataRepository {
    override suspend fun load(): LocalProfileData = LocalProfileData(
        profileName = "hibiki",
        episodeProgress = progressRepository.getAllPlaybackProgress(),
    )
}
