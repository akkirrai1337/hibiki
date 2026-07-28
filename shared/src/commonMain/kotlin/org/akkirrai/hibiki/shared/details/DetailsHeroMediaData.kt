package org.akkirrai.hibiki.shared.details

import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeTrailer
import org.akkirrai.hibiki.shared.model.TitleWatchState

data class DetailsHeroMediaData(
    val trailer: AnimeTrailer?,
    val resumeProgress: Float,
)

fun resolveDetailsHeroMediaData(
    anime: Anime,
    resumeState: TitleWatchState?,
): DetailsHeroMediaData {
    val resumeProgress = resumeState?.let {
        if (it.durationMs > 0L) {
            (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    } ?: 0f

    return DetailsHeroMediaData(
        trailer = anime.trailer?.takeIf { it.playbackUrl != null },
        resumeProgress = resumeProgress,
    )
}
