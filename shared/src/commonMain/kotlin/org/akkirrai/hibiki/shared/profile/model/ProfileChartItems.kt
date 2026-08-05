package org.akkirrai.hibiki.shared.profile

fun List<DistributionSegment>.toProfileGenreBarItems(): List<ProfileGenreBarItem> = map { segment ->
    ProfileGenreBarItem(
        label = segment.label,
        count = segment.count,
        color = segment.color,
    )
}

fun List<ActivityDay>.toProfileActivityBarItems(): List<ProfileActivityBarItem> = map { day ->
    ProfileActivityBarItem(
        dateLabel = day.dateLabel,
        episodeCount = day.episodeCount,
    )
}
