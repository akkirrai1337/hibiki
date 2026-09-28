package org.akkirrai.hibiki.feature.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akkirrai.hibiki.R
import org.akkirrai.hibiki.core.design.component.AppModalBottomSheet
import org.akkirrai.hibiki.core.design.component.filter.AppCollapsibleFilterSection
import org.akkirrai.hibiki.core.design.component.filter.AppFilterChip
import org.akkirrai.hibiki.core.download.DownloadPreference
import org.akkirrai.hibiki.core.model.PlaybackSubtitle
import org.akkirrai.hibiki.core.model.WatchEpisode
import org.akkirrai.hibiki.core.model.WatchSource
import org.akkirrai.hibiki.core.source.AnimeWatchRepository

/** The stream/subtitle/voiceover options resolved for one episode, before the user has picked among them. */
private sealed interface DownloadOptionsResult {
    data object Loading : DownloadOptionsResult
    data class Error(val message: String) : DownloadOptionsResult
    data class Loaded(
        val voiceovers: List<WatchSource>,
        val qualityLabels: List<String>,
        val subtitles: List<PlaybackSubtitle>,
    ) : DownloadOptionsResult
}

/**
 * Opens whenever the user starts an episode download, letting them pick a voiceover, a quality
 * and a subtitle track before it's queued (rather than always downloading whatever the fastest
 * player happened to return). None of these are known upfront -- they only exist once a stream is
 * resolved -- so this sheet resolves one itself on open instead of reusing the resolve download
 * queue does later for the actual download.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadOptionsSheet(
    source: WatchSource,
    episode: WatchEpisode,
    animeWatchRepository: AnimeWatchRepository,
    initialPreference: DownloadPreference?,
    onDismissRequest: () -> Unit,
    onConfirm: (source: WatchSource, episode: WatchEpisode, quality: String?, subtitleLanguages: Set<String>, remember: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var currentSource by remember { mutableStateOf(source) }
    var currentEpisode by remember { mutableStateOf(episode) }
    var result by remember(currentSource.sourceId, currentEpisode.id) {
        mutableStateOf<DownloadOptionsResult>(DownloadOptionsResult.Loading)
    }
    var selectedQuality by remember(currentSource.sourceId, currentEpisode.id) { mutableStateOf<String?>(null) }
    var selectedSubtitleLanguages by remember(currentSource.sourceId, currentEpisode.id) { mutableStateOf<Set<String>>(emptySet()) }
    var rememberForTitle by remember { mutableStateOf(initialPreference != null) }
    val genericError = stringResource(R.string.download_options_error)

    LaunchedEffect(currentSource.sourceId, currentEpisode.id) {
        result = try {
            val (playback, options) = withContext(Dispatchers.IO) {
                animeWatchRepository.resolveStream(sourceId = currentSource.sourceId, episodeId = currentEpisode.id) to
                    animeWatchRepository.getPlaybackSettingsOptions(sourceId = currentSource.sourceId, episodeId = currentEpisode.id)
            }
            // Matches how the player itself builds its quality picker: the resolved stream alone
            // usually reports only the one quality it picked, while the per-link quality labels
            // from getPlaybackSettingsOptions carry the other variants the source actually offers.
            val qualityLabels = (
                options.links.mapNotNull { it.qualityLabel } +
                    playback.availableQualityLabels +
                    listOfNotNull(playback.qualityLabel)
            ).map(String::trim).filter(String::isNotBlank).distinct()
            selectedQuality = initialPreference?.qualityLabel?.takeIf { it in qualityLabels }
                ?: playback.qualityLabel?.takeIf { it in qualityLabels }
                ?: qualityLabels.firstOrNull()
            selectedSubtitleLanguages = initialPreference?.subtitleLanguages
                ?.filterTo(mutableSetOf()) { language -> playback.subtitles.any { it.language == language } }
                ?: emptySet()
            DownloadOptionsResult.Loaded(
                voiceovers = options.voiceovers,
                qualityLabels = qualityLabels,
                subtitles = playback.subtitles,
            )
        } catch (error: Throwable) {
            DownloadOptionsResult.Error(error.message?.takeIf(String::isNotBlank) ?: genericError)
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.download_options_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (val current = result) {
                DownloadOptionsResult.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DownloadOptionsResult.Error -> {
                    Text(
                        text = current.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DownloadOptionsResult.Loaded -> {
                    val initialSourceId = source.sourceId
                    if (current.voiceovers.size > 1) {
                        AppCollapsibleFilterSection(
                            title = stringResource(R.string.download_options_voiceover),
                            onLongClick = {
                                val initial = current.voiceovers.firstOrNull { it.sourceId == initialSourceId }
                                if (initial != null && initial.sourceId != currentSource.sourceId) {
                                    currentSource = initial
                                }
                            },
                        ) {
                            DownloadOptionRow {
                                current.voiceovers.forEach { voiceover ->
                                    DownloadOptionChip(
                                        label = voiceover.title,
                                        selected = voiceover.sourceId == currentSource.sourceId,
                                        onClick = {
                                            if (voiceover.sourceId == currentSource.sourceId) return@DownloadOptionChip
                                            result = DownloadOptionsResult.Loading
                                            val episodeNumber = currentEpisode.number
                                            scope.launch {
                                                // Voiceovers are separate episode lists; match by
                                                // number like the player's own voiceover switch
                                                // does, since ids differ per source.
                                                val episodes = withContext(Dispatchers.IO) {
                                                    animeWatchRepository.getEpisodes(voiceover.sourceId)
                                                }
                                                val matching = episodes.firstOrNull { it.number == episodeNumber }
                                                    ?: episodes.firstOrNull()
                                                if (matching != null) {
                                                    currentSource = voiceover
                                                    currentEpisode = matching
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (current.qualityLabels.size > 1) {
                        AppCollapsibleFilterSection(
                            title = stringResource(R.string.download_options_quality),
                            onLongClick = { selectedQuality = current.qualityLabels.firstOrNull() },
                        ) {
                            DownloadOptionRow {
                                current.qualityLabels.forEach { quality ->
                                    DownloadOptionChip(
                                        label = quality,
                                        selected = quality == selectedQuality,
                                        onClick = { selectedQuality = quality },
                                    )
                                }
                            }
                        }
                    }

                    if (current.subtitles.isNotEmpty()) {
                        AppCollapsibleFilterSection(
                            title = stringResource(R.string.download_options_subtitles),
                            onLongClick = { selectedSubtitleLanguages = emptySet() },
                        ) {
                            DownloadOptionRow {
                                current.subtitles.forEach { subtitle ->
                                    val label = subtitle.label?.takeIf(String::isNotBlank)
                                        ?: subtitle.language?.takeIf(String::isNotBlank)
                                        ?: stringResource(R.string.download_options_subtitles_unknown)
                                    // No subtitle track has a language, and its label is not unique
                                    // enough to key a selection by -- there is simply nothing to
                                    // toggle for it, so it can't be picked (matches how it can never
                                    // end up in preferredSubtitleLanguages downstream either).
                                    val language = subtitle.language
                                    DownloadOptionChip(
                                        label = label,
                                        selected = language != null && language in selectedSubtitleLanguages,
                                        onClick = {
                                            if (language == null) return@DownloadOptionChip
                                            selectedSubtitleLanguages = if (language in selectedSubtitleLanguages) {
                                                selectedSubtitleLanguages - language
                                            } else {
                                                selectedSubtitleLanguages + language
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    RememberForTitleRow(
                        checked = rememberForTitle,
                        onCheckedChange = { rememberForTitle = it },
                    )

                    Button(
                        onClick = {
                            onConfirm(currentSource, currentEpisode, selectedQuality, selectedSubtitleLanguages, rememberForTitle)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.watch_download))
                    }
                }
            }
        }
    }
}

/** A rounded card row (bookmark icon + label + switch), matching the sheet's chip-and-card look
 * instead of a bare checkbox square that read as an unstyled leftover from a form. */
@Composable
private fun RememberForTitleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onCheckedChange(!checked) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (checked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.download_options_remember),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DownloadOptionRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** The filter sheet's included-chip color and "+ " prefix, reused as-is so a picked option reads
 * the same way a picked filter does -- the plain color swap alone was too subtle to read as a tap
 * having registered. */
private val DownloadOptionSelectedColor = Color(0xFF80DF87)

@Composable
private fun DownloadOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) DownloadOptionSelectedColor else MaterialTheme.colorScheme.tertiary
    val text = if (selected) "+ $label" else label
    AppFilterChip(color = color, icon = null, text = text, onClick = onClick)
}
