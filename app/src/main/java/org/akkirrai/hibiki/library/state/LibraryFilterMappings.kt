package org.akkirrai.hibiki.library.state

import org.akkirrai.hibiki.library.*
import org.akkirrai.hibiki.library.ui.libraryStatusAlias

import org.akkirrai.hibiki.search.model.AnimeSearchFilters

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
