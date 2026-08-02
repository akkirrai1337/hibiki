package org.akkirrai.beakokit.playback

import io.ktor.client.HttpClient
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.playback.extractor.AksorExtractor
import org.akkirrai.beakokit.playback.extractor.CvhExtractor
import org.akkirrai.beakokit.playback.extractor.DirectHlsExtractor
import org.akkirrai.beakokit.playback.extractor.DirectMp4Extractor
import org.akkirrai.beakokit.playback.extractor.KodikExtractor
import org.akkirrai.beakokit.playback.extractor.SibnetExtractor

/**
 * Extractors available on every BeakoKit target.
 *
 * JVM-only extractors are intentionally not hidden here: callers that have a platform
 * implementation can append them, while iOS gets the complete KMP-compatible stack.
 */
fun commonPlaybackExtractors(client: HttpClient): List<StreamExtractor> = listOf(
    DirectHlsExtractor(),
    DirectMp4Extractor(),
    KodikExtractor(client),
    AksorExtractor(client),
    CvhExtractor(client),
    SibnetExtractor(client),
)
