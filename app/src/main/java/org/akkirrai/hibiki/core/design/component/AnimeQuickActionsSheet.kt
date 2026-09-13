package org.akkirrai.hibiki.core.design.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.akkirrai.hibiki.core.design.component.anime.PosterImage
import org.akkirrai.hibiki.core.model.Anime

data class AnimeQuickAction(@param:StringRes val titleRes: Int, @param:StringRes val descriptionRes: Int, val icon: ImageVector, val isDestructive: Boolean = false, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeQuickActionsSheet(anime: Anime, actions: List<AnimeQuickAction>, onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun dismiss(after: () -> Unit) { scope.launch { sheetState.hide(); after() } }
    AppModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    PosterImage(
                        primaryUrl = anime.posterUrl, fallbackUrl = anime.posterFallbackUrl, contentDescription = null, modifier = Modifier.fillMaxSize(),
                        placeholder = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
                Text(anime.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            actions.forEach { action ->
                val color = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Surface(onClick = { dismiss(action.onClick) }, shape = RoundedCornerShape(18.dp), color = if (action.isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(action.icon, null, tint = color); Column { Text(stringResource(action.titleRes), color = color); Text(stringResource(action.descriptionRes), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
