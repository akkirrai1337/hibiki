package org.akkirrai.animeresolver.network

import io.ktor.client.statement.HttpResponse
import org.akkirrai.beakokit.http.bodyOrThrow as beakoBodyOrThrow
import org.akkirrai.beakokit.http.toReadableHttpErrorDetails as toBeakoReadableHttpErrorDetails

/** Compatibility wrapper for the HTTP helper that now belongs to BeakoKit. */
suspend inline fun <reified T> HttpResponse.bodyOrThrow(source: String): T =
    beakoBodyOrThrow(source)

/** Compatibility wrapper for the HTTP helper that now belongs to BeakoKit. */
fun String.toReadableHttpErrorDetails(): String = toBeakoReadableHttpErrorDetails()
