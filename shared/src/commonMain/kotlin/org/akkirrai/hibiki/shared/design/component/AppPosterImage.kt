package org.akkirrai.hibiki.shared.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.Image
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.SuccessResult

data class PosterImageLoadError(
    val stage: String,
    val url: String?,
    val fallbackUrl: String?,
    val throwable: Throwable?,
)

@Composable
fun AppPosterImage(
    primaryUrl: String?,
    fallbackUrl: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onImageSuccess: ((Image) -> Unit)? = null,
    onImageError: ((PosterImageLoadError) -> Unit)? = null,
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
            onSuccess = { state ->
                isLoading = false
                val image = (state.result as? SuccessResult)?.image
                if (image != null) onImageSuccess?.invoke(image)
            },
            onError = { state ->
                val failedUrl = activeUrl
                val canUseFallback = failedUrl == normalizedPrimary &&
                    normalizedFallback != null && normalizedFallback != normalizedPrimary

                onImageError?.invoke(
                    PosterImageLoadError(
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
