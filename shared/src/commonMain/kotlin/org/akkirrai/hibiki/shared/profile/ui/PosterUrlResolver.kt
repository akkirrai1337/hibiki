package org.akkirrai.hibiki.shared.profile

fun normalizePosterUrl(rawUrl: String?): String? = rawUrl?.trim()?.takeIf {
    it.startsWith("http://", true) || it.startsWith("https://", true)
}
