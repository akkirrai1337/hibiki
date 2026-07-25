package org.akkirrai.hibiki.shared.profile

/** Platform-neutral read boundary for the local profile feature. */
interface LocalProfileDataRepository {
    suspend fun load(): LocalProfileData
}
