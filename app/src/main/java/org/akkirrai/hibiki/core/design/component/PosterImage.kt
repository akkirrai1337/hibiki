package org.akkirrai.hibiki.core.design.component

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.asDrawable
import coil3.request.ErrorResult
import coil3.request.SuccessResult
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
    val context = LocalContext.current
    val normalizedPrimary = primaryUrl?.takeIf(String::isNotBlank)
    val normalizedFallback = fallbackUrl?.takeIf(String::isNotBlank)
    var activeUrl by remember(normalizedPrimary, normalizedFallback) {
        mutableStateOf(normalizedPrimary ?: normalizedFallback)
    }
    var isLoading by remember(normalizedPrimary, normalizedFallback) {
        mutableStateOf(activeUrl != null)
    }

    if (activeUrl == null) {
        placeholder()
        return
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = activeUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onLoading = { isLoading = true },
            onSuccess = { state ->
                isLoading = false
                val drawable = (state.result as? SuccessResult)?.image?.asDrawable(context.resources)
                if (drawable != null) onImageSuccess?.invoke(drawable)
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

        if (isLoading) {
            placeholder()
        }
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
