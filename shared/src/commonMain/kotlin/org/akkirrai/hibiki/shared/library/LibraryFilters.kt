package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.model.Anime

data class LibrarySearchFilters(
    val type: String? = null,
    val status: String? = null,
    val includedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
) {
    fun matches(entry: LibraryEntry): Boolean {
        val anime = entry.anime
        val typeMatches = type == null || anime.extractLibraryType() == type
        val statusMatches = status == null || anime.status.equals(status, ignoreCase = true)
        val animeGenres = anime.genres.map(String::trim).filter(String::isNotBlank).toSet()
        val includesMatch = includedGenres.isEmpty() || includedGenres.all { it in animeGenres }
        val excludesMatch = excludedGenres.none { it in animeGenres }
        return typeMatches && statusMatches && includesMatch && excludesMatch
    }

    fun hasActiveFilters(): Boolean =
        type != null || status != null || includedGenres.isNotEmpty() || excludedGenres.isNotEmpty()
}

data class LibraryFilterCatalog(
    val typeOptions: List<String> = emptyList(),
    val statusOptions: List<String> = emptyList(),
    val genreOptions: List<String> = emptyList(),
)

fun Anime.extractLibraryType(): String? {
    return subtitle
        .split(Regex("\\s*[â€¢Â·|]\\s*"))
        .map(String::trim)
        .firstOrNull { value ->
            value.isNotBlank() && value.any(Char::isLetter) && value.none(Char::isDigit)
        }
}
