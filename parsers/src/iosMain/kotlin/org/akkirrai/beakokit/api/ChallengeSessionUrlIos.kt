package org.akkirrai.beakokit.api

import io.ktor.http.Url

internal actual fun isAbsoluteHttpsUrl(value: String): Boolean = runCatching {
    val url = Url(value)
    url.protocol.name.equals("https", ignoreCase = true) && url.host.isNotBlank()
}.getOrDefault(false)
