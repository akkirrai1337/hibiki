package org.akkirrai.beakokit.api

import java.net.URI

internal actual fun isAbsoluteHttpsUrl(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull()
    return uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()
}
