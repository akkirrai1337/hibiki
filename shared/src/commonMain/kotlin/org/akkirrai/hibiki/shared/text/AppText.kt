package org.akkirrai.hibiki.shared.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.akkirrai.hibiki.shared.settings.LanguageMode

/** Stable keys used by shared UI instead of Android R.string identifiers. */
enum class AppTextKey {
    SharedUiReady,
    AppName,
    Home,
    Catalog,
    Search,
    Library,
    Sources,
    Profile,
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
    ThemeLight,
    ThemeDark,
    DesktopPreview,
    ProfileLibrary,
    ProfileEpisodes,
    Back,
    Favorite,
    Watch,
    WatchContinue,
    WatchContinueEpisode,
    WatchContinueEpisodePosition,
    Trailer,
    NextEpisodeCountdown,
    NextEpisodeCountdownNumbered,
    NextEpisodeEtaDaysHours,
    NextEpisodeEtaHoursMinutesSeconds,
    NextEpisodeEtaMinutesSeconds,
    Information,
    Status,
    Episodes,
    Type,
    ReleaseDate,
    SourceMaterial,
    Studio,
    Genres,
    Related,
    Similar,
    Announcement,
    Unknown,
    LibraryAddTitle,
    LibraryAddSubtitle,
    LibrarySavedNote,
    LibraryRemoveAction,
    LibraryWatching,
    LibraryPlanned,
    LibraryCompleted,
    LibraryDropped,
    LibraryOnHold,
    LibraryFavorite,
    LibrarySaved,
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
    private val systemLanguage: String = "en",
) : AppTextResolver {
    override fun resolve(key: AppTextKey): String {
        val russian = when (languageMode) {
            LanguageMode.RUSSIAN -> true
            LanguageMode.ENGLISH -> false
            LanguageMode.SYSTEM -> systemLanguage.lowercase().startsWith("ru")
        }
        return when (key) {
            AppTextKey.SharedUiReady -> if (russian) "Общий UI готов" else "Shared UI is ready"
            AppTextKey.AppName -> "hibiki"
            AppTextKey.Home -> if (russian) "Главная" else "Home"
            AppTextKey.Search -> if (russian) "Поиск" else "Search"
            AppTextKey.Library -> if (russian) "Библиотека" else "Library"
            AppTextKey.Catalog -> if (russian) "Каталог" else "Catalog"
            AppTextKey.Sources -> if (russian) "Источники" else "Sources"
            AppTextKey.Profile -> if (russian) "Профиль" else "Profile"
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
            AppTextKey.ThemeLight -> if (russian) "Тема: светлая" else "Theme: Light"
            AppTextKey.ThemeDark -> if (russian) "Тема: тёмная" else "Theme: Dark"
            AppTextKey.DesktopPreview -> if (russian) "Предпросмотр CMP для Desktop" else "CMP Desktop preview"
            AppTextKey.ProfileLibrary -> if (russian) "Библиотека" else "Library"
            AppTextKey.ProfileEpisodes -> if (russian) "Эпизоды" else "Episodes"
            AppTextKey.Back -> if (russian) "Назад" else "Back"
            AppTextKey.Favorite -> if (russian) "В библиотеку" else "Library"
            AppTextKey.Watch -> if (russian) "Смотреть" else "Watch"
            AppTextKey.WatchContinue -> if (russian) "Продолжить" else "Continue"
            AppTextKey.WatchContinueEpisode -> if (russian) "Продолжить · серия %s" else "Continue · Episode %s"
            AppTextKey.WatchContinueEpisodePosition -> if (russian) "Продолжить · серия %s · %s" else "Continue · Episode %s · %s"
            AppTextKey.Trailer -> if (russian) "Трейлер" else "Trailer"
            AppTextKey.NextEpisodeCountdown -> if (russian) "Следующая серия через %s" else "Next episode in %s"
            AppTextKey.NextEpisodeCountdownNumbered -> if (russian) "Серия %d через %s" else "Ep %d in %s"
            AppTextKey.NextEpisodeEtaDaysHours -> if (russian) "%dд %dч" else "%dd %dh"
            AppTextKey.NextEpisodeEtaHoursMinutesSeconds -> if (russian) "%dч %dм %dс" else "%dh %dm %ds"
            AppTextKey.NextEpisodeEtaMinutesSeconds -> if (russian) "%dм %dс" else "%dm %ds"
            AppTextKey.Information -> if (russian) "Информация" else "Information"
            AppTextKey.Status -> if (russian) "Статус" else "Status"
            AppTextKey.Episodes -> if (russian) "Эпизоды" else "Episodes"
            AppTextKey.Type -> if (russian) "Тип" else "Type"
            AppTextKey.ReleaseDate -> if (russian) "Дата выхода" else "Release date"
            AppTextKey.SourceMaterial -> if (russian) "Источник" else "Source material"
            AppTextKey.Studio -> if (russian) "Студия" else "Studio"
            AppTextKey.Genres -> if (russian) "Жанры" else "Genres"
            AppTextKey.Related -> if (russian) "Связанное" else "Related"
            AppTextKey.Similar -> if (russian) "Похожее" else "Similar"
            AppTextKey.Announcement -> if (russian) "Анонс" else "Announcement"
            AppTextKey.Unknown -> if (russian) "Неизвестно" else "Unknown"
            AppTextKey.LibraryAddTitle -> if (russian) "Добавить в библиотеку" else "Add to library"
            AppTextKey.LibraryAddSubtitle -> if (russian) "Выберите статус для этого тайтла" else "Choose a status for this title"
            AppTextKey.LibrarySavedNote -> if (russian) "Сохранённое управляется скачанными сериями. Чтобы убрать тайтл отсюда, удалите скачанные серии." else "Saved titles are managed by downloaded episodes. To remove a title from here, delete the downloaded episodes."
            AppTextKey.LibraryRemoveAction -> if (russian) "Удалить из библиотеки" else "Remove from library"
            AppTextKey.LibraryWatching -> if (russian) "Смотрю" else "Watching"
            AppTextKey.LibraryPlanned -> if (russian) "В планах" else "Planned"
            AppTextKey.LibraryCompleted -> if (russian) "Просмотрено" else "Completed"
            AppTextKey.LibraryDropped -> if (russian) "Брошено" else "Dropped"
            AppTextKey.LibraryOnHold -> if (russian) "Приостановлено" else "On hold"
            AppTextKey.LibraryFavorite -> if (russian) "Любимое" else "Favorite"
            AppTextKey.LibrarySaved -> if (russian) "Сохранённое" else "Saved"
        }
    }
}

val LocalAppTextResolver = staticCompositionLocalOf<AppTextResolver> {
    DefaultAppTextResolver(LanguageMode.SYSTEM)
}

@Composable
fun appText(key: AppTextKey): String = LocalAppTextResolver.current.resolve(key)
