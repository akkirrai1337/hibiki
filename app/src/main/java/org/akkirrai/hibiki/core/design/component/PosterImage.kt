package org.akkirrai.hibiki.core.design.component

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.asDrawable
import org.akkirrai.hibiki.core.log.AppLogger
import org.akkirrai.hibiki.shared.design.component.AppPosterImage
import org.akkirrai.hibiki.shared.design.component.PosterImageLoadError
import org.akkirrai.hibiki.shared.design.component.formatPosterLogUrl

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
    val resources = LocalContext.current.resources
    AppPosterImage(
        primaryUrl = primaryUrl,
        fallbackUrl = fallbackUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onImageSuccess = { image ->
            onImageSuccess?.invoke(image.asDrawable(resources))
        },
        onImageError = ::logPosterFailure,
        placeholder = placeholder,
    )
}

private fun logPosterFailure(error: PosterImageLoadError) {
    AppLogger.d(
        POSTER_LOG_TAG,
        buildString {
            append("[image.")
            append(error.stage)
            append("] url=")
            append(formatPosterLogUrl(error.url))
            append(" fallback=")
            append(formatPosterLogUrl(error.fallbackUrl))
            append(" error=")
            append(error.throwable?.javaClass?.simpleName ?: "null")
            error.throwable?.message?.takeIf(String::isNotBlank)?.let {
                append(" message=")
                append(it)
            }
        }
    )
}

private const val POSTER_LOG_TAG = "HibikiPoster"
