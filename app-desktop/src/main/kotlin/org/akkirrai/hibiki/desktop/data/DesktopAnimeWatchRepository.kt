package org.akkirrai.hibiki.desktop.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.akkirrai.beakokit.http.BeakoKitHttpPolicy
import org.akkirrai.beakokit.http.installBeakoKitHttpDefaults
import org.akkirrai.hibiki.shared.player.SharedAnimeWatchRepository
import org.akkirrai.hibiki.shared.player.WatchDataRepository

/** Desktop HTTP bridge for the shared playback source/runtime adapter. */
internal class DesktopAnimeWatchRepository(
    preferEnglish: Boolean = false,
) : WatchDataRepository by SharedAnimeWatchRepository(
    client = HttpClient(CIO) {
        installBeakoKitHttpDefaults(
            BeakoKitHttpPolicy(userAgent = "Hibiki/0.1 Desktop"),
        )
    },
    preferEnglish = preferEnglish,
)
