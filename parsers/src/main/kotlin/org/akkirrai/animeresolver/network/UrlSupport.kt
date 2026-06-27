package org.akkirrai.animeresolver.network

import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun normalizeUrl(url: String): String =
    when {
        url.startsWith("//") -> "https:$url"
        url.contains("://") -> url
        else -> "https://$url"
    }

fun resolveUrl(base: String, reference: String): String =
    when {
        reference.startsWith("http://") || reference.startsWith("https://") -> reference
        reference.startsWith("//") -> "${schemeOf(base)}:$reference"
        reference.startsWith("/") -> "${originOf(base)}$reference"
        else -> {
            val baseUrl = Url(normalizeUrl(base))
            val directory = baseDirectory(baseUrl.encodedPath)
            "${baseOrigin(baseUrl)}$directory$reference"
        }
    }

fun originOf(url: String): String =
    baseOrigin(Url(normalizeUrl(url)))

fun schemeOf(url: String): String =
    Url(normalizeUrl(url)).protocol.name

fun hostOf(url: String): String? =
    Url(normalizeUrl(url)).host

fun pathOf(url: String): String =
    Url(normalizeUrl(url)).encodedPath

fun isAbsoluteUrl(url: String): Boolean =
    url.startsWith("http://") || url.startsWith("https://")

@OptIn(ExperimentalEncodingApi::class)
fun decodeShiftedBase64(raw: String): String {
    if (raw.contains("//")) return raw
    val shifted = raw.map { ch ->
        if (ch.isLetter()) shiftLetter(ch) else ch
    }.joinToString("")
    val padded = shifted.padEnd(((shifted.length + 3) / 4) * 4, '=')
    return Base64.Default.decode(padded).decodeToString()
}

private fun baseOrigin(url: Url): String =
    buildString {
        append(url.protocol.name)
        append("://")
        append(url.host)
        append(portSuffix(url.port, url.protocol.name))
    }

private fun baseDirectory(path: String): String =
    when {
        path.isBlank() -> "/"
        path.endsWith("/") -> path
        else -> path.substringBeforeLast('/', missingDelimiterValue = "/").let { if (it.endsWith("/")) it else "$it/" }
    }

private fun portSuffix(port: Int, scheme: String): String =
    when {
        port <= 0 -> ""
        scheme == "https" && port == 443 -> ""
        scheme == "http" && port == 80 -> ""
        else -> ":$port"
    }

private fun shiftLetter(ch: Char): Char {
    val base = if (ch.isUpperCase()) 'A' else 'a'
    val shifted = (ch.code - base.code + 18) % 26 + base.code
    return shifted.toChar()
}
