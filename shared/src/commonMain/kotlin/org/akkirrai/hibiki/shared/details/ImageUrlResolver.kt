package org.akkirrai.hibiki.shared.details

fun String.toAbsoluteImageUrl(): String? {
    val value = trim().trim('"').replace("\\/", "/").takeIf(String::isNotBlank) ?: return null
    return when {
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> null
    }
}
