package org.akkirrai.animeresolver.network

/** Compatibility wrappers for URL helpers that now belong to BeakoKit. */
fun normalizeUrl(url: String): String = org.akkirrai.beakokit.http.normalizeUrl(url)

fun resolveUrl(base: String, reference: String): String =
    org.akkirrai.beakokit.http.resolveUrl(base, reference)

fun originOf(url: String): String = org.akkirrai.beakokit.http.originOf(url)

fun schemeOf(url: String): String = org.akkirrai.beakokit.http.schemeOf(url)

fun hostOf(url: String): String? = org.akkirrai.beakokit.http.hostOf(url)

fun pathOf(url: String): String = org.akkirrai.beakokit.http.pathOf(url)

fun isAbsoluteUrl(url: String): Boolean = org.akkirrai.beakokit.http.isAbsoluteUrl(url)

fun decodeShiftedBase64(raw: String): String = org.akkirrai.beakokit.http.decodeShiftedBase64(raw)
