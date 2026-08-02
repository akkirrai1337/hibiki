package org.akkirrai.beakokit.api

import java.net.URI

internal actual fun isHttpsConfigUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)
