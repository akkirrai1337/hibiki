package org.akkirrai.hibiki.shared.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.akkirrai.hibiki.shared.catalog.PrototypeAnimeCatalogRepository
import org.akkirrai.hibiki.shared.model.Anime
import org.akkirrai.hibiki.shared.text.AppTextKey
import org.akkirrai.hibiki.shared.text.appText

private enum class PrototypeTab(val textKey: AppTextKey) {
    HOME(AppTextKey.Home),
    SEARCH(AppTextKey.Search),
    LIBRARY(AppTextKey.Library),
    SETTINGS(AppTextKey.Settings),
}

@Composable
fun HibikiPrototypeApp(
    modifier: Modifier = Modifier,
    repository: AnimeCatalogRepository = PrototypeAnimeCatalogRepository,
) {
    var selectedTab by remember { mutableStateOf(PrototypeTab.HOME) }
    var query by remember { mutableStateOf("") }
    var catalogItems by remember(repository) { mutableStateOf(repository.initialItems) }

    LaunchedEffect(repository, query) {
        catalogItems = repository.search(query)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            val compact = maxWidth < 760.dp
            if (compact) {
                CompactPrototypeLayout(selectedTab, { selectedTab = it }, query, { query = it }, catalogItems)
            } else {
                WidePrototypeLayout(selectedTab, { selectedTab = it }, query, { query = it }, catalogItems)
            }
        }
    }
}

@Composable
private fun WidePrototypeLayout(
    selectedTab: PrototypeTab,
    onTabSelected: (PrototypeTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        PrototypeSidebar(selectedTab, onTabSelected)
        HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
        PrototypeContent(selectedTab, query, onQueryChange, items, Modifier.weight(1f))
    }
}

@Composable
private fun CompactPrototypeLayout(
    selectedTab: PrototypeTab,
    onTabSelected: (PrototypeTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
) {
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PrototypeTab.values().forEach { tab ->
                    FilterChip(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        label = { Text(appText(tab.textKey)) },
                    )
                }
            }
        },
    ) { padding ->
        PrototypeContent(selectedTab, query, onQueryChange, items, Modifier.padding(padding))
    }
}

@Composable
private fun PrototypeSidebar(selectedTab: PrototypeTab, onTabSelected: (PrototypeTab) -> Unit) {
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
        PrototypeTab.values().forEach { tab ->
            NavigationItem(tab, selectedTab == tab, { onTabSelected(tab) })
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "CMP Desktop preview",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NavigationItem(tab: PrototypeTab, selected: Boolean, onClick: () -> Unit) {
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
    selectedTab: PrototypeTab,
    query: String,
    onQueryChange: (String) -> Unit,
    items: List<Anime>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appText(selectedTab.textKey), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = if (selectedTab == PrototypeTab.SETTINGS) {
                        "Shared preferences and platform options"
                    } else {
                        "A shared catalog experience for Android and Windows"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selectedTab != PrototypeTab.SETTINGS) {
                Button(onClick = { }) { Text(appText(AppTextKey.ExploreCatalog)) }
            }
        }
        if (selectedTab == PrototypeTab.SETTINGS) {
            Spacer(Modifier.height(24.dp))
            PrototypeSettingsCard()
        } else {
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(appText(AppTextKey.SearchPlaceholder)) },
            )
            Spacer(Modifier.height(24.dp))
            SectionHeader(
                title = if (selectedTab == PrototypeTab.HOME) appText(AppTextKey.ContinueWatching) else appText(AppTextKey.ExploreCatalog),
                actionLabel = "See all",
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
                items(items) { anime -> AnimePrototypeCard(anime) }
            }
        }
    }
}

@Composable
private fun PrototypeSettingsCard() {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(UiDimens.MediumCorner),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Platform-ready preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "This shared screen is the place for language, theme, account, and playback settings. Android and Windows hosts can provide their own storage behind the same contract.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            TextButton(onClick = { }) { Text("Language: System") }
            TextButton(onClick = { }) { Text("Theme: Follow system") }
        }
    }
}

@Composable
private fun AnimePrototypeCard(anime: Anime) {
    androidx.compose.material3.Card(
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
