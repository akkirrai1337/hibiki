package org.akkirrai.hibiki.shared.source

import org.akkirrai.hibiki.shared.home.resolveDisplayTypeLabel

fun resolveAnimeSubtitle(
    type: String?,
    year: Int?,
    fallbackSubtitle: String?,
): String = listOfNotNull(
    type?.let(::resolveDisplayTypeLabel),
    year?.toString(),
).joinToString(" · ").ifBlank { fallbackSubtitle.orEmpty() }
