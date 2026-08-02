package org.akkirrai.hibiki.shared.catalog

fun defaultCatalogFilterYearRange(currentYear: Int): IntRange =
    CatalogFilterMinimumYear..(currentYear + 1)

private const val CatalogFilterMinimumYear = 1940
