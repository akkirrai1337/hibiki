package org.akkirrai.hibiki.shared.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akkirrai.hibiki.shared.design.UiDimens
import org.akkirrai.hibiki.shared.design.component.AppTonalSurface
import org.akkirrai.hibiki.shared.design.component.SectionHeader
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogRepository
import org.akkirrai.hibiki.shared.catalog.AnimeCatalogPresenter
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.design.HibikiDarkColorScheme
import org.akkirrai.hibiki.shared.design.HibikiLightColorScheme
import org.akkirrai.hibiki.shared.design.HibikiTypography
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.model.AnimeCatalogFilterCatalog
import org.akkirrai.hibiki.shared.model.AnimeSearchFilters
import org.akkirrai.hibiki.shared.settings.LanguageMode
import org.akkirrai.hibiki.shared.settings.AppSettingsState
import org.akkirrai.hibiki.shared.settings.AppSettingsStore
import org.akkirrai.hibiki.shared.settings.InMemoryAppSettingsStore
import org.akkirrai.hibiki.shared.text.DefaultAppTextResolver
import org.akkirrai.hibiki.shared.text.LocalAppTextResolver
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText
import org.akkirrai.hibiki.shared.navigation.AppDestination

@Composable
fun HibikiApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
    settingsStore: AppSettingsStore = InMemoryAppSettingsStore(),
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(repository) { AnimeCatalogPresenter(repository, scope) }
    val state by presenter.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AppDestination.HOME) }
    val initialSettings = remember(settingsStore) { settingsStore.load() }
    var languageMode by remember(settingsStore) { mutableStateOf(initialSettings.languageMode) }
    var darkTheme by remember(settingsStore) { mutableStateOf(initialSettings.darkTheme) }

    DisposableEffect(presenter) {
        presenter.loadFilterCatalog()
        presenter.search()
        onDispose { presenter.close() }
    }

    CompositionLocalProvider(LocalAppTextResolver provides DefaultAppTextResolver(languageMode)) {
        MaterialTheme(
            colorScheme = if (darkTheme) HibikiDarkColorScheme else HibikiLightColorScheme,
            typography = HibikiTypography,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints {
                    val compact = maxWidth < 760.dp
                    val onLanguageModeChange = { mode: LanguageMode ->
                        languageMode = mode
                        settingsStore.save(AppSettingsState(mode, darkTheme))
                    }
                    val onThemeChange = { dark: Boolean ->
                        darkTheme = dark
                        settingsStore.save(AppSettingsState(languageMode, dark))
                    }
                    if (compact) {
                    CompactPrototypeLayout(
                        selectedTab,
                        { selectedTab = it },
                        state.query,
                        presenter::onQueryChange,
                        state.items,
                        state.filters,
                        state.filterCatalog,
                        presenter::updateFilters,
                        state.selectedAnime,
                        presenter::openDetails,
                        presenter::closeDetails,
                        state.isDetailsLoading,
                        state.detailsError,
                        languageMode,
                        onLanguageModeChange,
                        darkTheme,
                        onThemeChange,
                    )
                    } else {
                    WidePrototypeLayout(
                        selectedTab,
                        { selectedTab = it },
                        state.query,
                        presenter::onQueryChange,
                        state.items,
                        state.filters,
                        state.filterCatalog,
                        presenter::updateFilters,
                        state.selectedAnime,
                        presenter::openDetails,
                        presenter::closeDetails,
                        state.isDetailsLoading,
                        state.detailsError,
                        languageMode,
                        onLanguageModeChange,
                        darkTheme,
                        onThemeChange,
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidePrototypeLayout(
    selectedTab: AppDestination,
    onTabSelected: (AppDestination) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        PrototypeSidebar(selectedTab, onTabSelected)
        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
        PrototypeContent(
            selectedTab,
            query,
            onQueryChange,
            items,
            filters,
            filterCatalog,
            onFiltersChange,
            selectedAnime,
            onAnimeClick,
            onBackFromDetails,
            isDetailsLoading,
            detailsError,
            languageMode,
            onLanguageModeChange,
            darkTheme,
            onThemeChange,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactPrototypeLayout(
    selectedTab: AppDestination,
    onTabSelected: (AppDestination) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AppDestination.entries.forEach { tab ->
                    FilterChip(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        label = { Text(appText(tab.textKey)) },
                    )
                }
            }
        },
    ) { padding ->
        PrototypeContent(
            selectedTab,
            query,
            onQueryChange,
            items,
            filters,
            filterCatalog,
            onFiltersChange,
            selectedAnime,
            onAnimeClick,
            onBackFromDetails,
            isDetailsLoading,
            detailsError,
            languageMode,
            onLanguageModeChange,
            darkTheme,
            onThemeChange,
            Modifier.padding(padding),
        )
    }
}

@Composable
private fun PrototypeSidebar(selectedTab: AppDestination, onTabSelected: (AppDestination) -> Unit) {
    Column(
        modifier = Modifier.width(220.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = appText(AppTextKey.AppName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = appText(AppTextKey.PrototypeNotice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        AppDestination.entries.forEach { tab ->
            NavigationItem(tab, selectedTab == tab, { onTabSelected(tab) })
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = appText(AppTextKey.DesktopPreview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationItem(tab: AppDestination, selected: Boolean, onClick: () -> Unit) {
    AppTonalSurface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Text(
            text = appText(tab.textKey),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PrototypeContent(
    selectedTab: AppDestination,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    selectedAnime: Anime?,
    onAnimeClick: (Anime) -> Unit,
    onBackFromDetails: () -> Unit,
    isDetailsLoading: Boolean,
    detailsError: String?,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appText(selectedTab.textKey), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (selectedTab == AppDestination.SETTINGS) {
                        appText(AppTextKey.SettingsSubtitle)
                    } else {
                        appText(AppTextKey.PrototypeSubtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedTab != AppDestination.SETTINGS) {
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
        if (selectedAnime != null) {
            AnimeDetailsPanel(selectedAnime, isDetailsLoading, detailsError, onBackFromDetails)
        } else {
            when (selectedTab) {
                AppDestination.HOME -> HomeScreen(
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                )
                AppDestination.SEARCH -> SearchScreen(
                    query = query,
                    onQueryChange = onQueryChange,
                    items = items,
                    filters = filters,
                    filterCatalog = filterCatalog,
                    onFiltersChange = onFiltersChange,
                    onAnimeClick = onAnimeClick,
                )
                AppDestination.LIBRARY -> LibraryScreen(
                    items = items,
                    onAnimeClick = onAnimeClick,
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    CatalogScreenContent(
        query = query,
        onQueryChange = onQueryChange,
        items = items,
        filters = filters,
        filterCatalog = filterCatalog,
        onFiltersChange = onFiltersChange,
        onAnimeClick = onAnimeClick,
        sectionTitle = appText(AppTextKey.ContinueWatching),
    )
}

@Composable
private fun ColumnScope.SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
) {
    CatalogScreenContent(
        query = query,
        onQueryChange = onQueryChange,
        items = items,
        filters = filters,
        filterCatalog = filterCatalog,
        onFiltersChange = onFiltersChange,
        onAnimeClick = onAnimeClick,
        sectionTitle = appText(AppTextKey.ExploreCatalog),
    )
}

@Composable
private fun ColumnScope.LibraryScreen(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = appText(AppTextKey.Library),
            actionLabel = appText(AppTextKey.SeeAll),
            onActionClick = { },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 210.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items) { anime -> AnimePrototypeCard(anime, onClick = { onAnimeClick(anime) }) }
        }
    }
}

@Composable
private fun SettingsScreen(
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    PrototypeSettingsCard(languageMode, onLanguageModeChange, darkTheme, onThemeChange)
}

@Composable
private fun ColumnScope.CatalogScreenContent(
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    filters: AnimeSearchFilters,
    filterCatalog: AnimeCatalogFilterCatalog?,
    onFiltersChange: (AnimeSearchFilters) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    sectionTitle: String,
) {
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(appText(AppTextKey.SearchPlaceholder)) },
    )
    if (filterCatalog?.genreOptions?.isNotEmpty() == true) {
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filterCatalog.genreOptions) { option ->
                FilterChip(
                    selected = option.id in filters.includedGenreAliases,
                    onClick = {
                        val selected = option.id in filters.includedGenreAliases
                        onFiltersChange(
                            filters.copy(
                                includedGenreAliases = if (selected) {
                                    filters.includedGenreAliases - option.id
                                } else {
                                    filters.includedGenreAliases + option.id
                                },
                            ),
                        )
                    },
                    label = { Text(option.title) },
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    SectionHeader(
        title = sectionTitle,
        actionLabel = appText(AppTextKey.SeeAll),
        onActionClick = { },
    )
    Spacer(Modifier.height(12.dp))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 210.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items) { anime -> AnimePrototypeCard(anime, onClick = { onAnimeClick(anime) }) }
    }
}

@Composable
private fun PrototypeSettingsCard(
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(appText(AppTextKey.SettingsTitle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                appText(AppTextKey.SettingsDescription),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            TextButton(
                onClick = {
                    onLanguageModeChange(
                        when (languageMode) {
                            LanguageMode.RUSSIAN -> LanguageMode.ENGLISH
                            LanguageMode.ENGLISH -> LanguageMode.RUSSIAN
                            LanguageMode.SYSTEM -> LanguageMode.RUSSIAN
                        },
                    )
                },
            ) {
                Text(
                    when (languageMode) {
                        LanguageMode.SYSTEM -> appText(AppTextKey.LanguageSystem)
                        LanguageMode.ENGLISH -> appText(AppTextKey.LanguageEnglish)
                        LanguageMode.RUSSIAN -> appText(AppTextKey.LanguageRussian)
                    },
                )
            }
            TextButton(onClick = { onThemeChange(!darkTheme) }) {
                Text(if (darkTheme) appText(AppTextKey.ThemeDark) else appText(AppTextKey.ThemeLight))
            }
        }
    }
}

@Composable
private fun AnimePrototypeCard(anime: Anime, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(topStart = UiDimens.MediumCorner, topEnd = UiDimens.MediumCorner))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = anime.title.take(1),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(anime.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${anime.status} · ${anime.episodesLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AnimeDetailsPanel(anime: Anime, isLoading: Boolean, error: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← ${appText(AppTextKey.Home)}") }
        androidx.compose.material3.Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(UiDimens.MediumCorner),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(anime.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(anime.subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${anime.status} · ${anime.episodesLabel}", color = MaterialTheme.colorScheme.primary)
                if (isLoading) {
                    CircularProgressIndicator()
                }
                error?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                anime.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
                if (anime.genres.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(anime.genres) { genre ->
                            FilterChip(selected = false, onClick = { }, label = { Text(genre) })
                        }
                    }
                }
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
    }
}
