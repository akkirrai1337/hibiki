package org.akkirrai.beakokit.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException
import org.akkirrai.beakokit.api.SourceUnavailableException
import org.akkirrai.beakokit.api.FallbackStreamExtractor
import org.akkirrai.beakokit.api.StreamExtractor
import org.akkirrai.beakokit.api.StreamValidator
import org.akkirrai.beakokit.model.PlayerLink
import org.akkirrai.beakokit.model.StreamValidationResult
import org.akkirrai.beakokit.model.VideoStream

/** A playable stream together with the player link and successful validation that produced it. */
data class ResolvedPlaybackStream(
    val link: PlayerLink,
    val stream: VideoStream,
    val validation: StreamValidationResult,
    val availableQualityLabels: List<String>,
)

sealed class PlaybackResolutionException(
    message: String,
    statusCode: Int? = null,
    cause: Throwable? = null,
) : SourceException(message, statusCode, cause, SourceErrorKind.UNAVAILABLE)

class NoPlayerLinksException : PlaybackResolutionException("Source did not provide any player links")

class NoSupportedExtractorException : PlaybackResolutionException(
    "No extractor supports the source player links",
)

class ExtractorFailedException(
    val playerName: String?,
    cause: Throwable? = null,
) : PlaybackResolutionException(
    message = "Player ${playerName.orFallbackName()} could not provide a playable stream",
    cause = cause,
)

class BlockedPlaybackUrlException(
    val playerName: String?,
    statusCode: Int,
    cause: Throwable? = null,
) : PlaybackResolutionException(
    message = "Player ${playerName.orFallbackName()} returned a blocked stream URL (HTTP $statusCode)",
    statusCode = statusCode,
    cause = cause,
)

/**
 * Resolves player links without leaking a single extractor failure to the caller while alternatives remain.
 * A cancellation is always propagated unchanged.
 */
class PlaybackResolver(
    private val extractors: List<StreamExtractor>,
    private val validator: StreamValidator,
) {
    suspend fun resolve(
        links: List<PlayerLink>,
        excludedStreamUrls: Set<String> = emptySet(),
        preferredQuality: String? = null,
        attemptTimeoutMillis: (PlayerLink) -> Long = { DEFAULT_ATTEMPT_TIMEOUT_MILLIS },
    ): ResolvedPlaybackStream {
        if (links.isEmpty()) throw NoPlayerLinksException()

        val failures = mutableListOf<Throwable>()
        var supportedLinkSeen = false
        for (link in links) {
            val matchingExtractors = extractors.filter { it.supports(link) }
            if (matchingExtractors.isEmpty()) continue
            supportedLinkSeen = true

            try {
                val timeout = attemptTimeoutMillis(link)
                require(timeout > 0) { "Playback attempt timeout must be positive" }
                val resolved = withTimeout(timeout) {
                    val validationFailures = mutableListOf<StreamValidationResult>()
                    val primary = matchingExtractors.firstOrNull { it !is FallbackStreamExtractor }
                    val fallbacks = matchingExtractors.filterIsInstance<FallbackStreamExtractor>()
                    val attempts = buildList<StreamExtractor> {
                        primary?.let(::add)
                        addAll(fallbacks)
                    }

                    var successful: ResolvedPlaybackStream? = null
                    for (extractor in attempts) {
                        if (extractor is FallbackStreamExtractor &&
                            !extractor.shouldAttempt(link, validationFailures)
                        ) {
                            continue
                        }
                        val streams = try {
                            extractor.extractVariants(link)
                                .filterNot { it.url in excludedStreamUrls }
                                .sortedWith(streamQualityComparator(preferredQuality))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            failures += error
                            continue
                        }
                        if (streams.isEmpty()) {
                            failures += ExtractorFailedException(link.playerName)
                            continue
                        }

                        for (stream in streams) {
                            val validation = validator.validate(stream)
                            if (validation.success) {
                                successful = ResolvedPlaybackStream(
                                    link = link,
                                    stream = stream,
                                    validation = validation,
                                    availableQualityLabels = streams.mapNotNull { candidate ->
                                        candidate.quality?.trim()?.takeIf(String::isNotBlank)
                                    }.distinct(),
                                )
                                break
                            } else {
                                validationFailures += validation
                                failures += validation.toFailure(link.playerName)
                            }
                        }
                        if (successful != null) break
                    }
                    successful
                }
                if (resolved != null) return resolved
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failures += error
            }
        }

        if (!supportedLinkSeen) throw NoSupportedExtractorException()
        throw selectFailure(failures)
    }

    private fun selectFailure(failures: List<Throwable>): Throwable {
        val meaningfulFailures = failures.filterNot { it is ExtractorFailedException && it.cause == null }
        val selected = meaningfulFailures.firstOrNull() ?: failures.firstOrNull()
            ?: return ExtractorFailedException(playerName = null)

        return when {
            meaningfulFailures.isNotEmpty() && meaningfulFailures.all { it is SourceUnavailableException } ->
                SourceUnavailableException("All source player URLs are unavailable", selected)
            meaningfulFailures.isNotEmpty() && meaningfulFailures.all { it is BlockedPlaybackUrlException } ->
                selected
            selected is PlaybackResolutionException || selected is SourceUnavailableException -> selected
            else -> ExtractorFailedException(playerName = null, cause = selected)
        }.also { finalError ->
            failures.filterNot { it === finalError }.forEach(finalError::addSuppressed)
        }
    }

    private fun streamQualityComparator(preferredQuality: String?) =
        compareByDescending<VideoStream> { it.quality.matchesPreferredQuality(preferredQuality) }
            .thenByDescending { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }

    private fun StreamValidationResult.toFailure(playerName: String?): PlaybackResolutionException =
        when (statusCode) {
            401, 403 -> BlockedPlaybackUrlException(playerName, statusCode, SourceException(message, statusCode))
            else -> ExtractorFailedException(playerName, SourceException(message, statusCode))
        }

    private fun String?.matchesPreferredQuality(preferredQuality: String?): Boolean =
        !preferredQuality.isNullOrBlank() && equals(preferredQuality, ignoreCase = true)

    private companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MILLIS = 8_000L
    }
}

private fun String?.orFallbackName(): String = this?.takeIf(String::isNotBlank) ?: "unknown"
