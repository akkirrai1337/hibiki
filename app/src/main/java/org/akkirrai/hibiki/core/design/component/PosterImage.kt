package org.akkirrai.hibiki.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import androidx.compose.material3.MaterialTheme
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

    when {
        normalizedPrimary != null -> {
            SubcomposeAsyncImage(
                model = normalizedPrimary,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                loading = { placeholder() },
                onError = { state ->
                    logPosterFailure(
                        stage = "primary",
                        url = normalizedPrimary,
                        fallbackUrl = normalizedFallback,
                        throwable = (state.result as? ErrorResult)?.throwable,
                    )
                },
                error = {
                    if (normalizedFallback != null && normalizedFallback != normalizedPrimary) {
                        SubcomposeAsyncImage(
                            model = normalizedFallback,
                            contentDescription = contentDescription,
                            modifier = modifier,
                            contentScale = contentScale,
                            loading = { placeholder() },
                            onError = { state ->
                                logPosterFailure(
                                    stage = "fallback",
                                    url = normalizedFallback,
                                    fallbackUrl = null,
                                    throwable = (state.result as? ErrorResult)?.throwable,
                                )
                            },
                            error = { placeholder() }
                        )
                    } else {
                        logPosterFailure(
                            stage = "primary-no-fallback",
                            url = normalizedPrimary,
                            fallbackUrl = normalizedFallback,
                            throwable = null,
                        )
                        placeholder()
                    }
                }
            )
        }

        normalizedFallback != null -> {
            SubcomposeAsyncImage(
                model = normalizedFallback,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                loading = { placeholder() },
                onError = { state ->
                    logPosterFailure(
                        stage = "fallback-only",
                        url = normalizedFallback,
                        fallbackUrl = null,
                        throwable = (state.result as? ErrorResult)?.throwable,
                    )
                },
                error = { placeholder() }
            )
        }

        else -> placeholder()
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
