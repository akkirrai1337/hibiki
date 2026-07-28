package org.akkirrai.hibiki.shared.profile

fun buildProfileActivityDays(
    activity: List<DailyWatchActivity>,
    dateStrings: List<String>,
    dateLabel: (String) -> String,
): List<ActivityDay> {
    val activityByDate = activity.associateBy(DailyWatchActivity::date)
    return dateStrings.map { date ->
        ActivityDay(
            dateLabel = dateLabel(date),
            episodeCount = activityByDate[date]?.let { entry ->
                entry.completedEpisodes.takeIf { it > 0 } ?: if (entry.watchedMs > 0L) 1 else 0
            } ?: 0,
        )
    }
}
