package org.akkirrai.hibiki.shared.catalog

import org.akkirrai.hibiki.shared.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters

fun AnimeSearchFilters.applyCatalogFilterDraft(
    animeType: AnimeTypeAlias?,
    includedStatuses: Set<String>,
    yearRange: IntRange,
    defaultYearRange: IntRange,
    capabilities: AnimeCatalogCapabilities,
    showGenreFilters: Boolean,
): AnimeSearchFilters = copy(
    typeAlias = animeType?.alias
        ?.takeIf { capabilities.supports(AnimeCatalogFilter.TYPE) },
    statusAlias = includedStatuses.firstOrNull()
        ?.takeIf { capabilities.supports(AnimeCatalogFilter.STATUS) },
    includedGenreAliases = includedGenreAliases
        .takeIf { showGenreFilters && capabilities.supports(AnimeCatalogFilter.INCLUDED_GENRES) }
        .orEmpty(),
    excludedGenreAliases = excludedGenreAliases
        .takeIf { showGenreFilters && capabilities.supports(AnimeCatalogFilter.EXCLUDED_GENRES) }
        .orEmpty(),
    yearFrom = yearRange.first
        .takeIf { capabilities.supports(AnimeCatalogFilter.YEAR_RANGE) && yearRange != defaultYearRange },
    yearTo = yearRange.last
        .takeIf { capabilities.supports(AnimeCatalogFilter.YEAR_RANGE) && yearRange != defaultYearRange },
)
