package org.akkirrai.hibiki.shared.source

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.beakokit.model.AnimeSearchFilterCatalog
import org.akkirrai.beakokit.model.SearchFilterOption

class YummySearchFilterLocalizerTest {
    @Test
    fun localizesGenreAliasesWithTheAndroidDictionary() {
        val localized = YummySearchFilterLocalizer.localize(
            catalog = AnimeSearchFilterCatalog(
                genreOptions = listOf(
                    SearchFilterOption("al-ternativnaya-istoriya", "al-ternativnaya-istoriya"),
                    SearchFilterOption("romantika", "romantika"),
                    SearchFilterOption("unknown_alias", "unknown_alias"),
                ),
            ),
            preferEnglish = false,
        )

        assertEquals(
            listOf("\u0410\u043b\u044c\u0442\u0435\u0440\u043d\u0430\u0442\u0438\u0432\u043d\u0430\u044f \u0438\u0441\u0442\u043e\u0440\u0438\u044f", "\u0420\u043e\u043c\u0430\u043d\u0442\u0438\u043a\u0430", "unknown_alias"),
            localized.genreOptions.map(SearchFilterOption::title),
        )
    }
}
