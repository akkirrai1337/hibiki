package org.akkirrai.hibiki.shared.library

import org.akkirrai.hibiki.shared.model.Anime

data class LibrarySearchFilters(
    val type: String? = null,
    val status: String? = null,
    val includedGenres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
) {
    fun matches(entry: LibraryEntry): Boolean {
        val anime = entry.anime
        val typeMatches = type == null || anime.extractLibraryType() == type
        val statusMatches = status == null || anime.status.equals(status, ignoreCase = true)
        val animeGenres = anime.genres.map(String::trim).filter(String::isNotBlank).toSet()
        val includesMatch = includedGenres.isEmpty() || includedGenres.all { it in animeGenres }
        val excludesMatch = excludedGenres.none { it in animeGenres }
        val year = anime.extractLibraryYear()
        val yearMatches = (yearFrom == null && yearTo == null) ||
            (year != null && yearFrom?.let { year >= it } != false && yearTo?.let { year <= it } != false)
        return typeMatches && statusMatches && includesMatch && excludesMatch && yearMatches
    }

    fun hasActiveFilters(): Boolean =
        type != null || status != null || includedGenres.isNotEmpty() || excludedGenres.isNotEmpty() ||
            yearFrom != null || yearTo != null
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

fun Anime.extractLibraryYear(): Int? = Regex("\\b(?:19|20)\\d{2}\\b")
    .find(listOfNotNull(subtitle, releaseDate).joinToString(" "))
    ?.value
    ?.toIntOrNull()
