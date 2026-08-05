package org.akkirrai.hibiki.shared.profile

data class ProfileActivitySummary(
    val activeDaysCount: Int,
    val totalEpisodes: Int,
    val watchedMs: Long,
)

fun LocalProfileData.activitySummary(): ProfileActivitySummary = ProfileActivitySummary(
    activeDaysCount = activity.count { it.completedEpisodes > 0 || it.watchedMs > 0L },
    totalEpisodes = activity.sumOf { it.completedEpisodes },
    watchedMs = activity.sumOf { it.watchedMs },
)
