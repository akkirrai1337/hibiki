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
    SearchFilters,
    SearchRetry,
    CatalogSortTitle,
    CatalogSortAlphabetical,
    CatalogSortPopular,
    CatalogSortUpdated,
    FilterUnavailable,
    FilterAllYears,
    FilterFromYear,
    FilterToYear,
    FilterReset,
    FilterApply,
    CatalogError,
    LibraryEmptyTitle,
    LibraryEmptyBody,
    LibraryFilteredEmptyTitle,
    LibrarySearchEmptyTitle,
    LibraryFilteredEmptyBody,
    HomeSearchEmptyTitle,
    HomeSearchEmptyBody,
    HomeSearchLoadMore,
    HomeRecentlyWatched,
    HomeRecentlyAdded,
    HomeContinueTitle,
    HomeContinueEmptyTitle,
    HomeContinueEmptyBody,
    HomeContinueOpenHint,
    HomePersonalEmptyTitle,
    HomePersonalEmptyBody,
    HomeBrowseCatalog,
    SettingsAppearance,
    SettingsTheme,
    SettingsSystemColorScheme,
    SettingsAmoled,
    SettingsPreferences,
    SettingsLanguage,
    SettingsNotifications,
    SettingsNotificationsStatus,
    SettingsPlayer,
    SettingsAutoSkip,
    PlayerSettingsRoot,
    PlayerSettingsSpeed,
    PlayerSettingsVoiceover,
    PlayerSettingsPlayer,
    PlayerSettingsQuality,
    PlayerSettingsAutoSkip,
    PlayerSettingsAutoPlayNext,
    PlayerSettingsOn,
    PlayerSettingsOff,
    PlayerEpisodeNumber,
    PlayerErrorTitle,
    PlayerRetry,
    PlayerLock,
    PlayerUnlock,
    PlayerPictureInPicture,
    PlayerSkip,
    PlayerWatch,
    SettingsExperimental,
    SettingsDiscord,
    DiscordBrowserSignIn,
    DiscordManualToken,
    DiscordInvalidToken,
    DiscordDisconnect,
    DiscordStatusDisabled,
    DiscordStatusSignedOut,
    DiscordStatusChecking,
    DiscordStatusConnecting,
    DiscordStatusConnected,
    DiscordStatusError,
    Cancel,
    Apply,
    SettingsUpdates,
    SettingsCheckUpdates,
    SettingsSupport,
    SettingsExportLogs,
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
    ProfileTabOverview,
    ProfileTabActivity,
    ProfileTabFavorites,
    ProfileName,
    ProfileEdit,
    ProfileSave,
    ProfileChangeAvatar,
    ProfileStatTotal,
    ProfileStatDays,
    ProfileStatTime,
    ProfileRecent,
    ProfileEmptyRecent,
    ProfileEmptyFavorites,
    ProfileAnalyticsWatchTime,
    ProfileAnalyticsTotal,
    ProfileAnalyticsGenres,
    ProfileAnalyticsGenresLabel,
    ProfileAnalyticsWatched,
    ProfileActivity,
    ProfileDateToday,
    ProfileDateYesterday,
    ProfileDateDaysAgo,
    Back,
    Favorite,
    DetailsFavorite,
    Watch,
    WatchSourcesEmptyTitle,
    WatchSourcesEmptyMessage,
    WatchSourcesLoadMore,
    WatchEpisodesEmptyTitle,
    WatchSourceFallback,
    WatchEpisodeHeadline,
    WatchEpisodeHeadlineWatched,
    WatchStatusWatched,
    WatchDownload,
    WatchPause,
    WatchRemoveDownload,
    WatchResume,
    WatchDownloaded,
    WatchStatusQueued,
    WatchStatusDownloading,
    WatchStatusPaused,
    WatchStatusFailed,
    WatchContinue,
    EpisodesShort,
    WatchContinueEpisode,
    WatchContinueEpisodePosition,
    Trailer,
    DetailsTrailer,
    NextEpisodeCountdown,
    NextEpisodeCountdownNumbered,
    NextEpisodeEtaDaysHours,
    NextEpisodeEtaHoursMinutesSeconds,
    NextEpisodeEtaMinutesSeconds,
    Information,
    Status,
    Episodes,
    EpisodesReleased,
    Type,
    ReleaseDate,
    SourceMaterial,
    SourceMaterialManga,
    SourceMaterialManhwa,
    SourceMaterialManhua,
    SourceMaterialLightNovel,
    SourceMaterialWebNovel,
    SourceMaterialVisualNovel,
    SourceMaterialGame,
    SourceMaterialOriginal,
    Studio,
    Genres,
    Related,
    Similar,
    Announcement,
    Movie,
    Ongoing,
    Released,
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
    OnboardingWelcomeTitle,
    OnboardingWelcomeDescription,
    OnboardingGetStarted,
    OnboardingSourceTitle,
    OnboardingSourceDescription,
    OnboardingSourceLanguageRussian,
    OnboardingSourceLanguageEnglish,
    OnboardingSourceLanguagesRussianEnglish,
    OnboardingNotificationsTitle,
    OnboardingNotificationsDescription,
    OnboardingNotificationsAllow,
    OnboardingNotificationsEnabled,
    OnboardingNotificationsDenied,
    OnboardingBack,
    OnboardingNext,
    OnboardingDone,
}

interface AppTextResolver {
    fun resolve(key: AppTextKey): String

    fun formatSearchResultsCount(count: Int): String
}

/**
 * Temporary common resolver used by the shared UI proof. The SYSTEM mode is
 * intentionally deterministic until each host supplies its actual locale.
 */
class DefaultAppTextResolver(
    private val languageMode: LanguageMode,
    private val systemLanguage: String = "en",
) : AppTextResolver {
    private val russian = when (languageMode) {
        LanguageMode.RUSSIAN -> true
        LanguageMode.ENGLISH -> false
        LanguageMode.SYSTEM -> systemLanguage.lowercase().startsWith("ru")
    }

    override fun formatSearchResultsCount(count: Int): String = if (russian) {
        val lastTwoDigits = count % 100
        val suffix = when {
            count % 10 == 1 && lastTwoDigits != 11 -> "\u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442"
            count % 10 in 2..4 && lastTwoDigits !in 12..14 -> "\u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u0430"
            else -> "\u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u043e\u0432"
        }
        "$count $suffix"
    } else {
        "$count ${if (count == 1) "result" else "results"}"
    }

    override fun resolve(key: AppTextKey): String {
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
            AppTextKey.SearchFilters -> if (russian) "Фильтры поиска" else "Search filters"
            AppTextKey.SearchRetry -> if (russian) "Повторить" else "Retry"
            AppTextKey.CatalogSortTitle -> if (russian) "Сортировка" else "Sort catalog"
            AppTextKey.CatalogSortAlphabetical -> if (russian) "По алфавиту" else "Alphabetical"
            AppTextKey.CatalogSortPopular -> if (russian) "Популярные" else "Popular"
            AppTextKey.CatalogSortUpdated -> if (russian) "Обновлённые" else "Recently updated"
            AppTextKey.FilterUnavailable -> if (russian) "Фильтры недоступны" else "Filters unavailable"
            AppTextKey.FilterAllYears -> if (russian) "Все годы" else "All years"
            AppTextKey.FilterFromYear -> if (russian) "От" else "From"
            AppTextKey.FilterToYear -> if (russian) "До" else "To"
            AppTextKey.FilterReset -> if (russian) "Сбросить" else "Reset"
            AppTextKey.FilterApply -> if (russian) "Применить" else "Apply"
            AppTextKey.CatalogError -> if (russian) "Ошибка каталога" else "Catalog error"
            AppTextKey.LibraryEmptyTitle -> if (russian) "Библиотека пуста" else "Your library is empty"
            AppTextKey.LibraryEmptyBody -> if (russian) "Добавленные тайтлы появятся здесь" else "Titles you add will appear here"
            AppTextKey.LibraryFilteredEmptyTitle -> if (russian) "Ничего не найдено" else "Nothing found"
            AppTextKey.LibrarySearchEmptyTitle -> if (russian) "Поиск не дал результатов" else "No search results"
            AppTextKey.LibraryFilteredEmptyBody -> if (russian) "Попробуйте изменить фильтры" else "Try changing the filters"
            AppTextKey.HomeSearchEmptyTitle -> if (russian) "Ничего не найдено" else "Nothing found"
            AppTextKey.HomeSearchEmptyBody -> if (russian) "Попробуйте изменить запрос" else "Try changing your search"
            AppTextKey.HomeSearchLoadMore -> if (russian) "Загрузить ещё" else "Load more"
            AppTextKey.HomeRecentlyWatched -> if (russian) "Недавно просмотренные" else "Recently watched"
            AppTextKey.HomeRecentlyAdded -> if (russian) "Недавно добавленные" else "Recently added"
            AppTextKey.HomeContinueTitle -> if (russian) "Продолжить просмотр" else "Continue watching"
            AppTextKey.HomeContinueEmptyTitle -> if (russian) "Нет незавершённого просмотра" else "Nothing to continue"
            AppTextKey.HomeContinueEmptyBody -> if (russian) "Начните смотреть тайтл, и он появится здесь" else "Start watching a title and it will appear here"
            AppTextKey.HomeContinueOpenHint -> if (russian) "Открыть" else "Open"
            AppTextKey.HomePersonalEmptyTitle -> if (russian) "Ваша главная пока пуста" else "Your home is empty"
            AppTextKey.HomePersonalEmptyBody -> if (russian) "Начните смотреть или добавьте тайтл в библиотеку" else "Start watching or add a title to your library."
            AppTextKey.HomeBrowseCatalog -> if (russian) "Открыть каталог" else "Browse catalog"
            AppTextKey.SettingsAppearance -> if (russian) "Внешний вид" else "Appearance"
            AppTextKey.SettingsTheme -> if (russian) "Тема" else "Theme"
            AppTextKey.SettingsSystemColorScheme -> if (russian) "Системная цветовая схема" else "Use system color scheme"
            AppTextKey.SettingsAmoled -> "AMOLED"
            AppTextKey.SettingsPreferences -> if (russian) "Настройки" else "Preferences"
            AppTextKey.SettingsLanguage -> if (russian) "Язык" else "Language"
            AppTextKey.SettingsNotifications -> if (russian) "Уведомления" else "Notifications"
            AppTextKey.SettingsNotificationsStatus -> if (russian) "Разрешение ещё не запрашивалось" else "Permission has not been requested"
            AppTextKey.SettingsPlayer -> if (russian) "Плеер" else "Player"
            AppTextKey.SettingsAutoSkip -> if (russian) "Автопропуск опенинга/эндинга" else "Auto-skip opening/ending"
            AppTextKey.SettingsExperimental -> if (russian) "Экспериментальные" else "Experimental"
            AppTextKey.PlayerSettingsRoot -> if (russian) "Настройки" else "Settings"
            AppTextKey.PlayerSettingsSpeed -> if (russian) "Скорость" else "Speed"
            AppTextKey.PlayerSettingsVoiceover -> if (russian) "Озвучка" else "Voiceover"
            AppTextKey.PlayerSettingsPlayer -> if (russian) "Плеер" else "Player"
            AppTextKey.PlayerSettingsQuality -> if (russian) "Качество" else "Quality"
            AppTextKey.PlayerSettingsAutoSkip -> if (russian) "Автопропуск опенинга/эндинга" else "Auto-skip opening/ending"
            AppTextKey.PlayerSettingsAutoPlayNext -> if (russian) "Автопереход к следующей серии" else "Autoplay next episode"
            AppTextKey.PlayerSettingsOn -> if (russian) "Вкл" else "On"
            AppTextKey.PlayerSettingsOff -> if (russian) "Выкл" else "Off"
            AppTextKey.PlayerEpisodeNumber -> if (russian) "Серия %s" else "Episode %s"
            AppTextKey.PlayerErrorTitle -> if (russian) "Ошибка воспроизведения" else "Playback error"
            AppTextKey.PlayerRetry -> if (russian) "Повторить" else "Retry"
            AppTextKey.PlayerLock -> if (russian) "Заблокировать" else "Lock"
            AppTextKey.PlayerUnlock -> if (russian) "Разблокировать" else "Unlock"
            AppTextKey.PlayerPictureInPicture -> if (russian) "Картинка в картинке" else "Picture-in-picture"
            AppTextKey.PlayerSkip -> if (russian) "Пропустить" else "Skip"
            AppTextKey.PlayerWatch -> if (russian) "Смотреть" else "Watch"
            AppTextKey.SettingsDiscord -> "Discord Rich Presence"
            AppTextKey.DiscordBrowserSignIn -> if (russian) "Войти через Discord" else "Sign in with Discord"
            AppTextKey.DiscordManualToken -> if (russian) "Токен Discord" else "Discord token"
            AppTextKey.DiscordInvalidToken -> if (russian) "Токен недействителен или Discord недоступен" else "The token is invalid or Discord could not be reached"
            AppTextKey.DiscordDisconnect -> if (russian) "Выйти из Discord" else "Log out"
            AppTextKey.DiscordStatusDisabled -> if (russian) "Выключено" else "Disabled"
            AppTextKey.DiscordStatusSignedOut -> if (russian) "Аккаунт не подключён" else "Account is not connected"
            AppTextKey.DiscordStatusChecking -> if (russian) "Проверка аккаунта…" else "Checking account…"
            AppTextKey.DiscordStatusConnecting -> if (russian) "Подключение…" else "Connecting…"
            AppTextKey.DiscordStatusConnected -> if (russian) "Подключено" else "Connected"
            AppTextKey.DiscordStatusError -> if (russian) "Ошибка подключения; повторяем в фоне" else "Connection error; retrying silently"
            AppTextKey.Cancel -> if (russian) "Отмена" else "Cancel"
            AppTextKey.Apply -> if (russian) "Применить" else "Apply"
            AppTextKey.ProfileDateToday -> if (russian) "Сегодня" else "Today"
            AppTextKey.ProfileDateYesterday -> if (russian) "Вчера" else "Yesterday"
            AppTextKey.ProfileDateDaysAgo -> if (russian) "%d дн. назад" else "%d d ago"
            AppTextKey.SettingsUpdates -> if (russian) "Обновления" else "Updates"
            AppTextKey.SettingsCheckUpdates -> if (russian) "Проверить обновления" else "Check for updates"
            AppTextKey.SettingsSupport -> if (russian) "Поддержка" else "Support"
            AppTextKey.SettingsExportLogs -> if (russian) "Экспортировать логи" else "Export logs"
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
            AppTextKey.LanguageSystem -> if (russian) "Системный" else "System"
            AppTextKey.LanguageEnglish -> if (russian) "Английский" else "English"
            AppTextKey.LanguageRussian -> if (russian) "Русский" else "Russian"
            AppTextKey.ThemeSystem -> if (russian) "Системная" else "Follow system"
            AppTextKey.ThemeLight -> if (russian) "Светлая" else "Light"
            AppTextKey.ThemeDark -> if (russian) "Тёмная" else "Dark"
            AppTextKey.DesktopPreview -> if (russian) "Предпросмотр CMP для Desktop" else "CMP Desktop preview"
            AppTextKey.ProfileLibrary -> if (russian) "Библиотека" else "Library"
            AppTextKey.ProfileEpisodes -> if (russian) "Эпизоды" else "Episodes"
            AppTextKey.ProfileTabOverview -> if (russian) "Обзор" else "About"
            AppTextKey.ProfileTabActivity -> if (russian) "Активность" else "Active"
            AppTextKey.ProfileTabFavorites -> if (russian) "Любимое" else "Favorites"
            AppTextKey.ProfileName -> if (russian) "Имя" else "Name"
            AppTextKey.ProfileEdit -> if (russian) "Редактировать профиль" else "Edit profile"
            AppTextKey.ProfileSave -> if (russian) "Сохранить" else "Save"
            AppTextKey.ProfileChangeAvatar -> if (russian) "Сменить аватар" else "Change avatar"
            AppTextKey.ProfileStatTotal -> if (russian) "ВСЕГО\nАНИМЕ" else "TOTAL\nANIME"
            AppTextKey.ProfileStatDays -> if (russian) "ДНИ\nПРОСМОТРА" else "DAYS\nWATCHED"
            AppTextKey.ProfileStatTime -> if (russian) "ВРЕМЯ\nПРОСМОТРА" else "WATCH\nTIME"
            AppTextKey.ProfileRecent -> if (russian) "Недавние" else "Recent"
            AppTextKey.ProfileEmptyRecent -> "—"
            AppTextKey.ProfileEmptyFavorites -> if (russian) "Пока нет любимых тайтлов" else "No favourite titles yet"
            AppTextKey.ProfileAnalyticsWatchTime -> if (russian) "Время просмотра" else "Watch time"
            AppTextKey.ProfileAnalyticsTotal -> if (russian) "Всего" else "Total"
            AppTextKey.ProfileAnalyticsGenres -> if (russian) "Жанры" else "Genres"
            AppTextKey.ProfileAnalyticsGenresLabel -> if (russian) "Жанров" else "Genres"
            AppTextKey.ProfileAnalyticsWatched -> if (russian) "Просмотр" else "Watched"
            AppTextKey.ProfileActivity -> if (russian) "Активность" else "Activity"
            AppTextKey.Back -> if (russian) "Назад" else "Back"
            AppTextKey.Favorite -> if (russian) "В библиотеку" else "Library"
            AppTextKey.DetailsFavorite -> if (russian) "В избранное" else "Add to favorites"
            AppTextKey.Watch -> if (russian) "Смотреть" else "Watch"
            AppTextKey.WatchSourcesEmptyTitle -> if (russian) "Озвучки не найдены" else "No watch sources"
            AppTextKey.WatchSourcesEmptyMessage -> if (russian) "Для этого тайтла пока нет доступных источников." else "This title has no available sources yet."
            AppTextKey.WatchSourcesLoadMore -> if (russian) "Загрузить ещё" else "Load more"
            AppTextKey.WatchEpisodesEmptyTitle -> if (russian) "Серии не найдены" else "No episodes"
            AppTextKey.WatchSourceFallback -> if (russian) "Озвучка" else "Voiceover"
            AppTextKey.WatchEpisodeHeadline -> if (russian) "Серия %s" else "Episode %s"
            AppTextKey.WatchEpisodeHeadlineWatched -> if (russian) "✓ Серия %s" else "✓ Episode %s"
            AppTextKey.WatchStatusWatched -> if (russian) "Просмотрено" else "Watched"
            AppTextKey.WatchDownload -> if (russian) "Скачать" else "Download"
            AppTextKey.WatchPause -> if (russian) "Пауза" else "Pause"
            AppTextKey.WatchRemoveDownload -> if (russian) "Удалить загрузку" else "Remove download"
            AppTextKey.WatchResume -> if (russian) "Продолжить" else "Resume"
            AppTextKey.WatchDownloaded -> if (russian) "Скачано" else "Downloaded"
            AppTextKey.WatchStatusQueued -> if (russian) "В очереди" else "Queued"
            AppTextKey.WatchStatusDownloading -> if (russian) "Скачивается %s%%" else "Downloading %s%%"
            AppTextKey.WatchStatusPaused -> if (russian) "На паузе" else "Paused"
            AppTextKey.WatchStatusFailed -> if (russian) "Ошибка загрузки" else "Download failed"
            AppTextKey.WatchContinue -> if (russian) "Продолжить" else "Continue"
            AppTextKey.EpisodesShort -> if (russian) "сер." else "ep."
            AppTextKey.WatchContinueEpisode -> if (russian) "Продолжить · серия %s" else "Continue · Episode %s"
            AppTextKey.WatchContinueEpisodePosition -> if (russian) "Серия %s · %s" else "Episode %s · %s"
            AppTextKey.Trailer -> if (russian) "Трейлер" else "Trailer"
            AppTextKey.DetailsTrailer -> if (russian) "Воспроизвести трейлер" else "Play trailer"
            AppTextKey.NextEpisodeCountdown -> if (russian) "Следующая серия через %s" else "Next episode in %s"
            AppTextKey.NextEpisodeCountdownNumbered -> if (russian) "Серия %d через %s" else "Ep %d in %s"
            AppTextKey.NextEpisodeEtaDaysHours -> if (russian) "%dд %dч" else "%dd %dh"
            AppTextKey.NextEpisodeEtaHoursMinutesSeconds -> if (russian) "%dч %dм %dс" else "%dh %dm %ds"
            AppTextKey.NextEpisodeEtaMinutesSeconds -> if (russian) "%dм %dс" else "%dm %ds"
            AppTextKey.Information -> if (russian) "Информация" else "Information"
            AppTextKey.Status -> if (russian) "Статус" else "Status"
            AppTextKey.Episodes -> if (russian) "Эпизоды" else "Episodes"
            AppTextKey.EpisodesReleased -> if (russian) "Серий вышло" else "Episodes released"
            AppTextKey.Type -> if (russian) "Тип" else "Type"
            AppTextKey.ReleaseDate -> if (russian) "Дата выхода" else "Release date"
            AppTextKey.SourceMaterial -> if (russian) "Источник" else "Source material"
            AppTextKey.SourceMaterialManga -> if (russian) "Манга" else "Manga"
            AppTextKey.SourceMaterialManhwa -> if (russian) "Манхва" else "Manhwa"
            AppTextKey.SourceMaterialManhua -> if (russian) "Маньхуа" else "Manhua"
            AppTextKey.SourceMaterialLightNovel -> if (russian) "Ранобэ" else "Light novel"
            AppTextKey.SourceMaterialWebNovel -> if (russian) "Веб-новелла" else "Web novel"
            AppTextKey.SourceMaterialVisualNovel -> if (russian) "Визуальная новелла" else "Visual novel"
            AppTextKey.SourceMaterialGame -> if (russian) "Игра" else "Game"
            AppTextKey.SourceMaterialOriginal -> if (russian) "Оригинал" else "Original"
            AppTextKey.Studio -> if (russian) "Студия" else "Studio"
            AppTextKey.Genres -> if (russian) "Жанры" else "Genres"
            AppTextKey.Related -> if (russian) "Связанное" else "Related"
            AppTextKey.Similar -> if (russian) "Похожее" else "Similar"
            AppTextKey.Announcement -> if (russian) "Анонс" else "announcement"
            AppTextKey.Movie -> if (russian) "Фильм" else "Movie"
            AppTextKey.Ongoing -> if (russian) "Онгоинг" else "Ongoing"
            AppTextKey.Released -> if (russian) "Вышел" else "Released"
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
            AppTextKey.OnboardingWelcomeTitle -> if (russian) "Добро пожаловать в hibiki" else "Welcome to hibiki"
            AppTextKey.OnboardingWelcomeDescription -> if (russian) "Настроим приложение за пару шагов, чтобы вы могли сразу начать просмотр." else "Let’s set up the app in a couple of steps so you can start watching right away."
            AppTextKey.OnboardingGetStarted -> if (russian) "Начать настройку" else "Get started"
            AppTextKey.OnboardingSourceTitle -> if (russian) "Источник аниме" else "Anime source"
            AppTextKey.OnboardingSourceDescription -> if (russian) "Мы подобрали источники на основе языка вашего устройства." else "We selected sources based on your device language."
            AppTextKey.OnboardingSourceLanguageRussian -> if (russian) "Контент на русском языке" else "Russian-language content"
            AppTextKey.OnboardingSourceLanguageEnglish -> if (russian) "Контент на английском языке" else "English-language content"
            AppTextKey.OnboardingSourceLanguagesRussianEnglish -> if (russian) "Контент на русском и английском языках" else "Russian and English content"
            AppTextKey.OnboardingNotificationsTitle -> if (russian) "Уведомления" else "Notifications"
            AppTextKey.OnboardingNotificationsDescription -> if (russian) "Получайте уведомления после завершения загрузок." else "Get notified when downloads are complete."
            AppTextKey.OnboardingNotificationsAllow -> if (russian) "Разрешить уведомления" else "Allow notifications"
            AppTextKey.OnboardingNotificationsEnabled -> if (russian) "Уведомления включены" else "Notifications are enabled"
            AppTextKey.OnboardingNotificationsDenied -> if (russian) "Уведомления можно включить позже в системных настройках." else "You can enable notifications later in system settings."
            AppTextKey.OnboardingBack -> if (russian) "Назад" else "Back"
            AppTextKey.OnboardingNext -> if (russian) "Далее" else "Next"
            AppTextKey.OnboardingDone -> if (russian) "Готово" else "Done"
        }
    }
}

val LocalAppTextResolver = staticCompositionLocalOf<AppTextResolver> {
    DefaultAppTextResolver(LanguageMode.SYSTEM)
}

@Composable
fun appText(key: AppTextKey): String = LocalAppTextResolver.current.resolve(key)

@Composable
fun appSearchResultsCount(count: Int): String = LocalAppTextResolver.current.formatSearchResultsCount(count)
