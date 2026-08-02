package org.akkirrai.hibiki.shared.design.component

fun formatPosterLogUrl(url: String?): String =
    if (url.isNullOrBlank()) "null" else url.substringAfterLast('/')
