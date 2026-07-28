package org.akkirrai.hibiki.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import org.akkirrai.hibiki.core.design.icon
import org.akkirrai.hibiki.core.source.LibraryCategory
import org.akkirrai.hibiki.core.source.labelResId
import org.akkirrai.hibiki.core.source.LibraryRepository

@Composable
fun rememberLibraryStatusByAnimeId(): Map<String, LibraryCategory> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { LibraryRepository(context.applicationContext) }
    var statuses by remember(repository) { mutableStateOf(repository.getLibraryEntries().associateBy({ it.anime.id }, { it.category })) }

    DisposableEffect(lifecycleOwner, repository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statuses = repository.getLibraryEntries().associateBy({ it.anime.id }, { it.category })
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return statuses
}

@Composable
fun LibraryStatusPosterFooter(category: LibraryCategory) {
    val label = stringResource(category.labelResId)
    org.akkirrai.hibiki.shared.library.LibraryStatusPosterFooter(label, category.icon())
}
