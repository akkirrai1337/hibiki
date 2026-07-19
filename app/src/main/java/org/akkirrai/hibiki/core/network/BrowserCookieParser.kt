package org.akkirrai.hibiki.core.network

internal fun parseBrowserCookies(header: String?): LinkedHashMap<String, String> =
    header.orEmpty()
        .split(';')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = part.substring(0, separator).trim()
            val value = part.substring(separator + 1).trim()
            name.takeIf(String::isNotBlank)?.let { it to value }
        }
        .associateTo(LinkedHashMap()) { it }
