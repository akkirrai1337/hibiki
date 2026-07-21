package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ErrorResult
import org.akkirrai.hibiki.core.log.AppLogger

@Composable
fun PosterImage(
    primaryUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit,
) {
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
            onSuccess = { isLoading = false },
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
