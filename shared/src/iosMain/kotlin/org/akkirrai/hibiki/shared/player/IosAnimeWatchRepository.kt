package org.akkirrai.hibiki.shared.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults

/** iOS HTTP bridge for the shared playback source/runtime adapter. */
internal class IosAnimeWatchRepository(
    preferEnglish: Boolean = false,
) : WatchDataRepository by SharedAnimeWatchRepository(
    client = HttpClient(Darwin) {
        installBeakoKitHttpDefaults(
            BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 iOS"),
        )
    },
    preferEnglish = preferEnglish,
)
