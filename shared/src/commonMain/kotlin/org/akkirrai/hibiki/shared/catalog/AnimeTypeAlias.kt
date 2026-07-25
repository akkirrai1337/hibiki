package org.akkirrai.hibiki.shared.catalog

enum class AnimeTypeAlias(val alias: String) {
    Tv("tv"),
    Ona("ona"),
    Ova("ova"),
    Movie("movie"),
;

    companion object {
        fun fromAlias(alias: String?): AnimeTypeAlias? = entries.firstOrNull {
            it.alias == alias?.trim()?.lowercase()
        }
    }
}
