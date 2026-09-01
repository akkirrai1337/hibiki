package org.akkirrai.hibiki.core.design.component.anime

import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ErrorResult
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import org.akkirrai.hibiki.core.log.AppLogger

@Composable
fun PosterImage(
    primaryUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onImageSuccess: ((Drawable) -> Unit)? = null,
    placeholder: @Composable () -> Unit,
) {
    val normalizedPrimary = primaryUrl?.takeIf(String::isNotBlank)
    val normalizedFallback = fallbackUrl?.takeIf(String::isNotBlank)
    var activeUrl by remember(normalizedPrimary, normalizedFallback) {
        mutableStateOf(normalizedPrimary ?: normalizedFallback)
    }
    // The source card stays composed while a shared-element transition is in flight. Reuse the
    // drawable it has already decoded as this instance's placeholder, rather than exposing the
    // card/background for the frame or two before Coil completes the new request. For a trailer
    // banner, the poster fallback gives the same stable bridge until the trailer still is ready.
    var retainedDrawable by remember(normalizedPrimary, normalizedFallback) {
        mutableStateOf(
            cachedPosterDrawable(normalizedPrimary) ?: cachedPosterDrawable(normalizedFallback),
        )
    }
    var isLoading by remember(normalizedPrimary, normalizedFallback) {
        mutableStateOf(activeUrl != null && retainedDrawable == null)
    }
    // A shared-element transition (e.g. catalog card -> details poster) hands this composable a
    // *new* instance once it settles into its final spot, resetting isLoading to true even though
    // Coil already has the exact same URL in memory from the transition a frame earlier - the
    // cache hit resolves within a frame or two, but that's still enough for the placeholder to
    // flash visibly. Only actually showing it once loading has been underway for a bit filters out
    // that flash while still covering a genuinely slow/uncached load.
    var showPlaceholder by remember(normalizedPrimary, normalizedFallback) { mutableStateOf(false) }
    LaunchedEffect(isLoading, normalizedPrimary, normalizedFallback) {
        if (isLoading) {
            delay(PLACEHOLDER_FLASH_GUARD_MILLIS)
            showPlaceholder = true
        } else {
            showPlaceholder = false
        }
    }

    if (activeUrl == null) {
        placeholder()
        return
    }

    val retainedPainter = retainedDrawable?.let { drawable ->
        rememberPosterDrawablePainter(drawable)
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = activeUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            placeholder = retainedPainter,
            onLoading = { isLoading = retainedDrawable == null },
            onSuccess = { state ->
                isLoading = false
                (state.result as? SuccessResult)?.drawable?.let { drawable ->
                    retainedDrawable = drawable
                    cachePosterDrawable(activeUrl, drawable)
                    onImageSuccess?.invoke(drawable)
                }
            },
            onError = { state ->
                val failedUrl = activeUrl
                val canUseFallback = failedUrl == normalizedPrimary &&
                    normalizedFallback != null && normalizedFallback != normalizedPrimary

                logPosterFailure(
                    stage = when {
                        canUseFallback -> "primary"
                        failedUrl == normalizedFallback && normalizedPrimary == null -> "fallback-only"
                        failedUrl == normalizedFallback -> "fallback"
                        else -> "primary-no-fallback"
                    },
                    url = failedUrl,
                    fallbackUrl = normalizedFallback.takeIf { canUseFallback },
                    throwable = (state.result as? ErrorResult)?.throwable,
                )

                if (canUseFallback) {
                    activeUrl = normalizedFallback
                    isLoading = true
                }
            },
        )

        if (showPlaceholder) {
            placeholder()
        }
    }
}

/** Loading states shorter than this never show a placeholder, to avoid flashing one for an image that's already cached. */
private const val PLACEHOLDER_FLASH_GUARD_MILLIS = 120L

private val posterDrawableCache = mutableMapOf<String, WeakReference<Drawable>>()

private fun cachedPosterDrawable(url: String?): Drawable? = url
    ?.let { synchronized(posterDrawableCache) { posterDrawableCache[it]?.get() } }

private fun cachePosterDrawable(url: String?, drawable: Drawable) {
    if (url != null) synchronized(posterDrawableCache) {
        posterDrawableCache[url] = WeakReference(drawable)
    }
}

@Composable
private fun rememberPosterDrawablePainter(drawable: Drawable): Painter {
    val bitmap = remember(drawable) {
        (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
    }
    return remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
}

@Composable
fun PosterPlaceholder(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun logPosterFailure(
    stage: String,
    url: String?,
    fallbackUrl: String?,
    throwable: Throwable?,
) {
    AppLogger.d(
        POSTER_LOG_TAG,
        buildString {
            append("[image.")
            append(stage)
            append("] url=")
            append(url.shortPosterUrl())
            append(" fallback=")
            append(fallbackUrl.shortPosterUrl())
            append(" error=")
            append(throwable?.javaClass?.simpleName ?: "null")
            throwable?.message?.takeIf(String::isNotBlank)?.let {
                append(" message=")
                append(it)
            }
        }
    )
}

private fun String?.shortPosterUrl(): String {
    if (this.isNullOrBlank()) return "null"
    return substringAfterLast('/')
}

private const val POSTER_LOG_TAG = "HibikiPoster"
