package org.akkirrai.animeresolver.extractor

import org.akkirrai.animeresolver.core.PlayerExtractor
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.PlayerType
import org.akkirrai.beakokit.model.StreamType
import org.akkirrai.beakokit.model.VideoStream

class DirectHlsExtractor : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean = link.type == PlayerType.DIRECT_HLS

    override suspend fun extract(link: PlayerLink): VideoStream = VideoStream(
        url = link.url,
        type = StreamType.HLS,
        quality = link.quality,
        headers = link.headers,
    )
}

class DirectMp4Extractor : PlayerExtractor {
    override fun supports(link: PlayerLink): Boolean = link.type == PlayerType.DIRECT_MP4

    override suspend fun extract(link: PlayerLink): VideoStream = VideoStream(
        url = link.url,
        type = StreamType.MP4,
        quality = link.quality,
        headers = link.headers,
    )
}
