package org.akkirrai.hibiki.core.source

import android.content.Context
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.http.hostOf
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.VideoStream

/** Resolves the embed servers currently used by GogoAnime in Android's browser engine. */
class GogoAnimeWebViewExtractor(
    context: Context,
) : StreamExtractor {
    private val delegate = AnimePaheWebViewExtractor(context)

    override fun supports(link: PlayerLink): Boolean = isGogoAnimePlayerLink(link)

    override suspend fun extract(link: PlayerLink): VideoStream = delegate.extract(link)
}

internal fun isGogoAnimePlayerLink(link: PlayerLink): Boolean {
    if (link.type != PlayerType.EMBED) return false
    val host = hostOf(link.url)?.lowercase().orEmpty()
    return host == "gogoanime.me.uk" || host.endsWith(".gogoanime.me.uk") ||
        host == "vidmoly.biz" || host.endsWith(".vidmoly.biz") ||
        host == "bysesayeveum.com" || host.endsWith(".bysesayeveum.com")
}
