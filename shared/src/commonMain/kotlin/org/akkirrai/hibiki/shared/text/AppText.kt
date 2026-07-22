package org.akkirrai.hibiki.shared.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.akkirrai.hibiki.shared.settings.LanguageMode

/** Stable keys used by shared UI instead of Android R.string identifiers. */
enum class AppTextKey {
    SharedUiReady,
    AppName,
    Home,
    Search,
    Library,
    Settings,
    Featured,
    ContinueWatching,
    ExploreCatalog,
    SearchPlaceholder,
    PrototypeNotice,
}

interface AppTextResolver {
    fun resolve(key: AppTextKey): String
}

/**
 * Temporary common resolver used by the shared UI proof. The SYSTEM mode is
 * intentionally deterministic until each host supplies its actual locale.
 */
class DefaultAppTextResolver(
    private val languageMode: LanguageMode,
) : AppTextResolver {
    override fun resolve(key: AppTextKey): String {
        val russian = languageMode == LanguageMode.RUSSIAN
        return when (key) {
            AppTextKey.SharedUiReady -> if (russian) "Общий UI готов" else "Shared UI is ready"
            AppTextKey.AppName -> "hibiki"
            AppTextKey.Home -> if (russian) "Главная" else "Home"
            AppTextKey.Search -> if (russian) "Поиск" else "Search"
            AppTextKey.Library -> if (russian) "Библиотека" else "Library"
            AppTextKey.Settings -> if (russian) "Настройки" else "Settings"
            AppTextKey.Featured -> if (russian) "Рекомендуем" else "Featured"
            AppTextKey.ContinueWatching -> if (russian) "Продолжить просмотр" else "Continue watching"
            AppTextKey.ExploreCatalog -> if (russian) "Каталог" else "Explore catalog"
            AppTextKey.SearchPlaceholder -> if (russian) "Найти аниме" else "Search anime"
            AppTextKey.PrototypeNotice -> if (russian) {
                "Прототип: данные пока демонстрационные"
            } else {
                "Prototype: content is currently sample data"
            }
        }
    }
}

val LocalAppTextResolver = staticCompositionLocalOf<AppTextResolver> {
    DefaultAppTextResolver(LanguageMode.SYSTEM)
}

@Composable
fun appText(key: AppTextKey): String = LocalAppTextResolver.current.resolve(key)
