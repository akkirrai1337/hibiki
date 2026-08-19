package org.akkirrai.hibiki.profile

fun buildProfileAnalyticsPages(
    snapshot: LocalProfileSnapshot,
    watchTimeTitle: String,
    totalLabel: String,
    genresTitle: String,
    genresLabel: String,
): List<ProfileAnalyticsPage> = listOf(
    ProfileAnalyticsPage(
        title = watchTimeTitle,
        centerPrimary = snapshot.libraryTotal.toString(),
        centerSecondary = totalLabel,
        segments = snapshot.libraryStatusSegments.map { segment ->
            ProfileAnalyticsSegment(
                label = segment.label,
                valueLabel = segment.count.toString(),
                weight = segment.count.toFloat(),
                color = segment.color,
            )
        },
        legendColumns = 2,
    ),
    ProfileAnalyticsPage(
        title = genresTitle,
        centerPrimary = snapshot.genreSegments.sumOf(DistributionSegment::count).toString(),
        centerSecondary = genresLabel,
        segments = snapshot.genreSegments.map { segment ->
            ProfileAnalyticsSegment(
                label = segment.label,
                valueLabel = segment.count.toString(),
                weight = segment.count.toFloat(),
                color = segment.color,
            )
        },
        legendColumns = 3,
    ),
)
