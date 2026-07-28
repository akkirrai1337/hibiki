package org.akkirrai.hibiki.shared.player

fun formatShortUrl(url: String?): String {
    if (url.isNullOrBlank()) return "null"
    return url.substringBefore('?').substringAfterLast('/')
}
