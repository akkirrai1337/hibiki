package org.akkirrai.beakokit.http

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.akkirrai.beakokit.api.SourceErrorKind
import org.akkirrai.beakokit.api.SourceException

suspend inline fun <reified T> HttpResponse.bodyOrThrow(source: String): T {
    if (!status.isSuccess()) {
        val details = bodyAsText().take(500)
        val message = if (status.value == 429) {
            "$source временно ограничил частоту запросов (HTTP 429)"
        } else {
            "$source вернул HTTP ${status.value}"
        }
        val normalizedDetails = details.toReadableHttpErrorDetails()
        throw SourceException(
            message = if (normalizedDetails.isBlank()) message else "$message: $normalizedDetails",
            statusCode = status.value,
            kind = when (status.value) {
                401, 403 -> SourceErrorKind.AUTH
                404 -> SourceErrorKind.NOT_FOUND
                429 -> SourceErrorKind.RATE_LIMITED
                in 500..599 -> SourceErrorKind.NETWORK
                else -> SourceErrorKind.UNKNOWN
            },
        )
    }
    return body()
}

fun String.toReadableHttpErrorDetails(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""

    val looksLikeHtml = trimmed.startsWith("<!doctype", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        trimmed.contains("<head", ignoreCase = true) ||
        trimmed.contains("<body", ignoreCase = true)
    if (looksLikeHtml) {
        return "сервер вернул HTML-страницу ошибки"
    }

    return trimmed
        .replace(Regex("\\s+"), " ")
        .take(220)
}
