package org.akkirrai.hibiki.shared.player

fun formatHeaderNames(headers: Map<String, String>): String =
    if (headers.isEmpty()) {
        "[]"
    } else {
        headers.keys
            .filter(String::isNotBlank)
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
    }
