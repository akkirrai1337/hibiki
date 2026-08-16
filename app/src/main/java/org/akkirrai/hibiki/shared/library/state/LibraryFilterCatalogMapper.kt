package org.akkirrai.hibiki.shared.library.state

import org.akkirrai.hibiki.shared.library.*
import org.akkirrai.hibiki.shared.library.ui.libraryStatusAlias
import org.akkirrai.hibiki.shared.library.ui.libraryStatusLabel

import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogCapabilities
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilter
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.catalog.model.AnimeCatalogFilterOption

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
