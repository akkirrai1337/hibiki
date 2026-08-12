package org.akkirrai.hibiki.shared.text

import androidx.compose.runtime.Composable
import java.util.Locale

/** Compose Desktop reads its resource locale from the JVM default locale. */
@Composable
actual fun AppTextResourceLocale(
    languageTag: String,
    content: @Composable () -> Unit,
) {
    val locale = Locale.forLanguageTag(languageTag)
    if (Locale.getDefault().language != locale.language) {
        Locale.setDefault(locale)
    }
    content()
}
