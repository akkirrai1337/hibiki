package org.akkirrai.hibiki.library.state

import org.akkirrai.hibiki.library.*

import org.akkirrai.hibiki.catalog.model.Anime
import org.akkirrai.hibiki.catalog.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilter
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.catalog.model.AnimeCatalogFilterOption
import org.akkirrai.hibiki.library.ui.libraryStatusAlias
import org.akkirrai.hibiki.library.ui.libraryStatusLabel
import org.akkirrai.hibiki.search.model.AnimeSearchFilters

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
        .split(Regex("\\s*[·|]\\s*"))
        .map(String::trim)
        .firstOrNull { value ->
            value.isNotBlank() && value.any(Char::isLetter) && value.none(Char::isDigit)
        }
}

fun Anime.extractLibraryYear(): Int? = Regex("\\b(?:19|20)\\d{2}\\b")
    .find(listOfNotNull(subtitle, releaseDate).joinToString(" "))
    ?.value
    ?.toIntOrNull()

fun LibrarySearchFilters.toAnimeSearchFilters(): AnimeSearchFilters = AnimeSearchFilters(
    typeAlias = type?.lowercase(),
    statusAlias = status?.let(::libraryStatusAlias),
    includedGenreAliases = includedGenres,
    excludedGenreAliases = excludedGenres,
    yearFrom = yearFrom,
    yearTo = yearTo,
)

fun AnimeSearchFilters.toLibrarySearchFilters(catalog: LibraryFilterCatalog): LibrarySearchFilters =
    LibrarySearchFilters(
        type = typeAlias?.let { alias -> catalog.typeOptions.firstOrNull { it.equals(alias, ignoreCase = true) } },
        status = statusAlias?.let { alias -> catalog.statusOptions.firstOrNull { libraryStatusAlias(it) == alias } },
        includedGenres = includedGenreAliases,
        excludedGenres = excludedGenreAliases,
        yearFrom = yearFrom,
        yearTo = yearTo,
    )

fun buildLibraryFilterCatalog(
    typeOptions: List<String>,
    statusOptions: List<String>,
    genreOptions: List<String>,
    isRussian: Boolean,
): AnimeCatalogFilterCatalog {
    val statuses = statusOptions.map { status ->
        AnimeCatalogFilterOption(
            id = libraryStatusAlias(status),
            title = libraryStatusLabel(status, isRussian),
        )
    }.distinctBy(AnimeCatalogFilterOption::id)

    return AnimeCatalogFilterCatalog(
        typeOptions = typeOptions.map { AnimeCatalogFilterOption(it.lowercase(), it.uppercase()) },
        statusOptions = statuses,
        genreOptions = genreOptions.map { AnimeCatalogFilterOption(it, it) },
        capabilities = AnimeCatalogCapabilities(
            supportedFilters = setOf(
                AnimeCatalogFilter.TYPE,
                AnimeCatalogFilter.STATUS,
                AnimeCatalogFilter.INCLUDED_GENRES,
                AnimeCatalogFilter.EXCLUDED_GENRES,
                AnimeCatalogFilter.YEAR_RANGE,
            ),
        ),
    )
}
