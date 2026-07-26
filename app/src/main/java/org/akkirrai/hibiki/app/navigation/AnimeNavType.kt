package org.akkirrai.hibiki.app.navigation

import android.net.Uri
import org.akkirrai.hibiki.core.model.Anime

object AnimeNavType {
    const val DETAILS_ROUTE = "details"
    const val WATCH_SOURCES_ROUTE = "watch_sources"
    const val TRENDING_ROUTE = "trending"
    const val CATALOG_ROUTE = "catalog"
    const val RECENT_UPDATES_ROUTE = "recent_updates"
    const val SETTINGS_ROUTE = "settings"
    const val EPISODES_ROUTE = "episodes"
    const val PLAYER_ROUTE = "player"
    const val ID_ARG = "id"
    const val SOURCE_ID_ARG = "sourceId"
    const val EPISODE_ID_ARG = "episodeId"
    const val EPISODE_NUMBER_ARG = "episodeNumber"
    const val TITLE_ARG = "title"
    const val SUBTITLE_ARG = "subtitle"
    const val EPISODES_ARG = "episodes"
    const val STATUS_ARG = "status"
    const val POSTER_ARG = "poster"
    const val POSTER_FALLBACK_ARG = "posterFallback"
    const val SOURCE_TITLE_ARG = "sourceTitle"
    const val DOWNLOAD_MODE_ARG = "downloadMode"

    const val DETAILS_PATTERN =
        "$DETAILS_ROUTE/{$ID_ARG}?$TITLE_ARG={$TITLE_ARG}&$SUBTITLE_ARG={$SUBTITLE_ARG}&$EPISODES_ARG={$EPISODES_ARG}&$STATUS_ARG={$STATUS_ARG}&$POSTER_ARG={$POSTER_ARG}&$POSTER_FALLBACK_ARG={$POSTER_FALLBACK_ARG}"

    const val WATCH_SOURCES_PATTERN =
        "$WATCH_SOURCES_ROUTE/{$ID_ARG}?$TITLE_ARG={$TITLE_ARG}&$DOWNLOAD_MODE_ARG={$DOWNLOAD_MODE_ARG}"

    const val EPISODES_PATTERN =
        "$EPISODES_ROUTE/{$SOURCE_ID_ARG}?$SOURCE_TITLE_ARG={$SOURCE_TITLE_ARG}&$DOWNLOAD_MODE_ARG={$DOWNLOAD_MODE_ARG}"

    const val PLAYER_PATTERN =
        "$PLAYER_ROUTE/{$SOURCE_ID_ARG}?$EPISODE_ID_ARG={$EPISODE_ID_ARG}&$EPISODE_NUMBER_ARG={$EPISODE_NUMBER_ARG}"

    fun createDetailsRoute(anime: Anime): String {
        return buildString {
            append("$DETAILS_ROUTE/${Uri.encode(anime.id)}")
            append("?$TITLE_ARG=${Uri.encode(anime.title)}")
            append("&$SUBTITLE_ARG=${Uri.encode(anime.subtitle)}")
            append("&$EPISODES_ARG=${Uri.encode(anime.episodesLabel)}")
            append("&$STATUS_ARG=${Uri.encode(anime.status)}")
            append("&$POSTER_ARG=${Uri.encode(anime.posterUrl.orEmpty())}")
            append("&$POSTER_FALLBACK_ARG=${Uri.encode(anime.posterFallbackUrl.orEmpty())}")
        }
    }

    fun createWatchSourcesRoute(anime: Anime, downloadMode: Boolean = false): String {
        return buildString {
            append("$WATCH_SOURCES_ROUTE/${Uri.encode(anime.id)}")
            append("?$TITLE_ARG=${Uri.encode(anime.title)}")
            append("&$DOWNLOAD_MODE_ARG=$downloadMode")
        }
    }

    fun createEpisodesRoute(
        source: org.akkirrai.hibiki.core.model.WatchSource,
        downloadMode: Boolean = false,
    ): String {
        return buildString {
            append("$EPISODES_ROUTE/${Uri.encode(source.sourceId)}")
            append("?$SOURCE_TITLE_ARG=${Uri.encode(source.title)}")
            append("&$DOWNLOAD_MODE_ARG=$downloadMode")
        }
    }

    fun createPlayerRoute(sourceId: String, episodeId: String, episodeNumber: Double? = null): String {
        return buildString {
            append("$PLAYER_ROUTE/${Uri.encode(sourceId)}")
            append("?$EPISODE_ID_ARG=${Uri.encode(episodeId)}")
            append("&$EPISODE_NUMBER_ARG=${Uri.encode(episodeNumber?.toString().orEmpty())}")
        }
    }
}
