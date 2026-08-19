package org.akkirrai.hibiki.text

import androidx.compose.runtime.Composable

/**
 * Android's localized Context is supplied by HibikiSettingsProvider before the
 * shared UI is composed, so Compose Resources observes the selected app locale.
 */
@Composable
fun AppTextResourceLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) = content()
