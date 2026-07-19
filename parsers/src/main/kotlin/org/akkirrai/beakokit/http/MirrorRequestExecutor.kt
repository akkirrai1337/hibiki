package org.akkirrai.beakokit.http

import kotlinx.coroutines.CancellationException
import org.akkirrai.beakokit.api.SourceLogLevel
import org.akkirrai.beakokit.api.SourceLogger
import org.akkirrai.beakokit.api.SourceUnavailableException

/** Executes a request against ordered mirrors without swallowing coroutine cancellation. */
class MirrorRequestExecutor(
    private val sourceName: String,
    baseUrls: List<String>,
    private val logger: SourceLogger = SourceLogger.NONE,
) {
    private val baseUrls = baseUrls
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { it.trimEnd('/') }
        .distinct()
        .also { require(it.isNotEmpty()) { "At least one mirror is required" } }

    suspend fun <T> execute(request: suspend (baseUrl: String) -> T): T {
        val failures = mutableListOf<Throwable>()
        baseUrls.forEachIndexed { index, baseUrl ->
            try {
                return request(baseUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failures += error
                logger.log(
                    SourceLogLevel.WARNING,
                    "$sourceName mirror ${index + 1}/${baseUrls.size} failed",
                    error,
                )
            }
        }

        throw SourceUnavailableException(
            message = "$sourceName mirrors are unavailable",
            cause = failures.firstOrNull(),
        ).also { unavailable ->
            failures.drop(1).forEach(unavailable::addSuppressed)
        }
    }
}
