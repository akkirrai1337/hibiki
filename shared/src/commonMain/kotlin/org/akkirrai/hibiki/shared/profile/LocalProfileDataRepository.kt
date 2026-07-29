package org.akkirrai.hibiki.shared.profile

/** Platform-neutral read boundary for the local profile feature. */
interface LocalProfileDataRepository {
    suspend fun load(): LocalProfileData

    fun updateProfileName(name: String): String? = null

    fun updateProfileAvatar(uri: String) = Unit
}
