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
    PrototypeSubtitle,
    SeeAll,
    SettingsSubtitle,
    SettingsTitle,
    SettingsDescription,
    LanguageSystem,
    LanguageEnglish,
    LanguageRussian,
    ThemeSystem,
    DesktopPreview,
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
            AppTextKey.PrototypeSubtitle -> if (russian) {
                "Общий каталог для Android и Windows"
            } else {
                "A shared catalog experience for Android and Windows"
            }
            AppTextKey.SeeAll -> if (russian) "Все" else "See all"
            AppTextKey.SettingsSubtitle -> if (russian) {
                "Общие настройки и параметры платформы"
            } else {
                "Shared preferences and platform options"
            }
            AppTextKey.SettingsTitle -> if (russian) "Настройки hibiki" else "Platform-ready preferences"
            AppTextKey.SettingsDescription -> if (russian) {
                "Здесь будут настройки языка, темы, аккаунта и воспроизведения. Android и Windows смогут хранить их через общий контракт."
            } else {
                "This shared screen is the place for language, theme, account, and playback settings. Android and Windows hosts can provide their own storage behind the same contract."
            }
            AppTextKey.LanguageSystem -> if (russian) "Язык: системный" else "Language: System"
            AppTextKey.LanguageEnglish -> if (russian) "Язык: английский" else "Language: English"
            AppTextKey.LanguageRussian -> if (russian) "Язык: русский" else "Language: Russian"
            AppTextKey.ThemeSystem -> if (russian) "Тема: системная" else "Theme: Follow system"
            AppTextKey.DesktopPreview -> if (russian) "Предпросмотр CMP для Desktop" else "CMP Desktop preview"
        }
    }
}

val LocalAppTextResolver = staticCompositionLocalOf<AppTextResolver> {
    DefaultAppTextResolver(LanguageMode.SYSTEM)
}

@Composable
fun appText(key: AppTextKey): String = LocalAppTextResolver.current.resolve(key)
