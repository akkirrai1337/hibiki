package org.akkirrai.hibiki.profile

import androidx.compose.ui.graphics.Color

fun LocalProfileData.profileGenreTrackedTitlesCount(): Int =
    library
        .map { it.anime }
        .distinctBy { it.id }
        .count { it.genres.isNotEmpty() }

fun LocalProfileData.buildGenreSegments(): List<DistributionSegment> =
    library
        .map { it.anime }
        .distinctBy { it.id }
        .flatMap { it.genres }
        .groupingBy { it.trim() }
        .eachCount()
        .filterKeys(String::isNotBlank)
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(PROFILE_GENRE_SEGMENT_LIMIT)
        .mapIndexed { index, entry ->
            DistributionSegment(
                label = entry.key,
                count = entry.value,
                color = PROFILE_GENRE_PALETTE[index % PROFILE_GENRE_PALETTE.size],
            )
        }

private const val PROFILE_GENRE_SEGMENT_LIMIT = 6
private val PROFILE_GENRE_PALETTE = listOf(
    Color(0xFF48D67B),
    Color(0xFFF7BC16),
    Color(0xFFA56CE3),
    Color(0xFFFF646B),
    Color(0xFFC24ED3),
    Color(0xFF737373),
)
