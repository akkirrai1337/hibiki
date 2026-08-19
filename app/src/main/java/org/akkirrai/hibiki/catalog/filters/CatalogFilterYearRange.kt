package org.akkirrai.hibiki.catalog.filters

import org.akkirrai.hibiki.catalog.*

fun defaultCatalogFilterYearRange(currentYear: Int): IntRange =
    CatalogFilterMinimumYear..(currentYear + 1)

private const val CatalogFilterMinimumYear = 1940
