package org.akkirrai.beakokit.api

import io.ktor.http.Url

internal fun isValidHttpsUrl(value: String): Boolean {
    if ('\r' in value || '\n' in value) return false
    val parsed = runCatching { Url(value) }.getOrNull()
    val authority = value.substringAfter("//", missingDelimiterValue = "")
        .takeWhile { character -> character !in "/?#" }
    return parsed?.protocol?.name == "https" &&
        parsed.host.isNotBlank() &&
        authority.isNotBlank() &&
        '@' !in authority
}

internal fun requireValidHttpsUrl(value: String, label: String) {
    require(isValidHttpsUrl(value)) { "$label must be a valid HTTPS URL" }
}
