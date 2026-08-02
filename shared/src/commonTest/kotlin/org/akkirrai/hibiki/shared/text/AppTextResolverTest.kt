package org.akkirrai.hibiki.shared.text

import kotlin.test.Test
import kotlin.test.assertEquals
import org.akkirrai.hibiki.shared.settings.LanguageMode

class AppTextResolverTest {
    @Test
    fun englishTextIsResolvedForEnglishMode() {
        assertEquals(
            "Shared UI is ready",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.SharedUiReady),
        )
    }

    @Test
    fun russianTextIsResolvedForRussianMode() {
        assertEquals(
            "Общий UI готов",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.SharedUiReady),
        )
    }
    @Test
    fun systemModeUsesRussianHostLanguage() {
        assertEquals(
            "Общий UI готов",
            DefaultAppTextResolver(LanguageMode.SYSTEM, systemLanguage = "ru-RU")
                .resolve(AppTextKey.SharedUiReady),
        )
    }

    @Test
    fun systemModeFallsBackToEnglishForOtherHostLanguages() {
        assertEquals(
            "Shared UI is ready",
            DefaultAppTextResolver(LanguageMode.SYSTEM, systemLanguage = "uk-UA")
                .resolve(AppTextKey.SharedUiReady),
        )
    }

    @Test
    fun searchResultsCountMatchesEnglishAndRussianPluralRules() {
        assertEquals("1 result", DefaultAppTextResolver(LanguageMode.ENGLISH).formatSearchResultsCount(1))
        assertEquals("24 results", DefaultAppTextResolver(LanguageMode.ENGLISH).formatSearchResultsCount(24))
        assertEquals("1 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442", DefaultAppTextResolver(LanguageMode.RUSSIAN).formatSearchResultsCount(1))
        assertEquals("24 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u0430", DefaultAppTextResolver(LanguageMode.RUSSIAN).formatSearchResultsCount(24))
        assertEquals("25 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u043e\u0432", DefaultAppTextResolver(LanguageMode.RUSSIAN).formatSearchResultsCount(25))
    }

    @Test
    fun watchSourcesLabelsMatchAndroidReference() {
        val english = DefaultAppTextResolver(LanguageMode.ENGLISH)
        val russian = DefaultAppTextResolver(LanguageMode.RUSSIAN)

        assertEquals("No watch sources", english.resolve(AppTextKey.WatchSourcesEmptyTitle))
        assertEquals("Load more", english.resolve(AppTextKey.WatchSourcesLoadMore))
        assertEquals("Озвучки не найдены", russian.resolve(AppTextKey.WatchSourcesEmptyTitle))
        assertEquals("Загрузить ещё", russian.resolve(AppTextKey.WatchSourcesLoadMore))
        assertEquals("No episodes", english.resolve(AppTextKey.WatchEpisodesEmptyTitle))
        assertEquals("Озвучка", russian.resolve(AppTextKey.WatchSourceFallback))
        assertEquals("Episode %s", english.resolve(AppTextKey.WatchEpisodeHeadline))
        assertEquals("✓ Серия %s", russian.resolve(AppTextKey.WatchEpisodeHeadlineWatched))
        assertEquals("Просмотрено", russian.resolve(AppTextKey.WatchStatusWatched))
    }

    @Test
    fun homeContinueOpenHintMatchesAndroidReference() {
        assertEquals(
            "Tap to open title",
            DefaultAppTextResolver(LanguageMode.ENGLISH).resolve(AppTextKey.HomeContinueOpenHint),
        )
        assertEquals(
            "Нажмите, чтобы открыть тайтл",
            DefaultAppTextResolver(LanguageMode.RUSSIAN).resolve(AppTextKey.HomeContinueOpenHint),
        )
    }
}
