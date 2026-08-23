package org.akkirrai.hibiki.core.source

/** Returns a compact human-readable repository name without changing the stored URL. */
fun repositoryDisplayName(url: String): String {
    val normalized = url.trim().substringBefore('#').substringBefore('?').trimEnd('/')
    if (normalized.isBlank()) return url.trim()

    val authority = normalized.substringAfter("://", normalized).substringBefore('/')
    val host = authority.substringAfter('@').substringBefore(':').lowercase()
    val pathSegments = normalized
        .substringAfter("://", "")
        .substringAfter('/', "")
        .split('/')
        .filter(String::isNotBlank)
        .map { it.removeSuffix(".git") }

    val githubName = when {
        host == "github.com" && pathSegments.size >= 2 -> "${pathSegments[0]}/${pathSegments[1]}"
        host == "raw.githubusercontent.com" && pathSegments.size >= 2 -> "${pathSegments[0]}/${pathSegments[1]}"
        else -> null
    }
    if (!githubName.isNullOrBlank()) return githubName

    val meaningfulSegments = pathSegments
        .dropLastWhile { it.equals("index.json", ignoreCase = true) }
        .dropLastWhile { it.equals("repository", ignoreCase = true) }
    meaningfulSegments.lastOrNull()?.takeIf(String::isNotBlank)?.let { return it }

    host.takeIf(String::isNotBlank)?.let { return it.removePrefix("www.") }
    return normalized.take(MAX_REPOSITORY_DISPLAY_NAME_LENGTH)
        .let { if (normalized.length > it.length) "$it…" else it }
}

/** Returns the public repository page when it can be derived from an index URL. */
fun repositoryBrowseUrl(url: String): String {
    val normalized = url.trim().substringBefore('#').substringBefore('?').trimEnd('/')
    val authority = normalized.substringAfter("://", normalized).substringBefore('/')
    val host = authority.substringAfter('@').substringBefore(':').lowercase()
    val pathSegments = normalized
        .substringAfter("://", "")
        .substringAfter('/', "")
        .split('/')
        .filter(String::isNotBlank)

    val (owner, repository) = when {
        host == "github.com" && pathSegments.size >= 2 -> pathSegments[0] to pathSegments[1]
        host == "raw.githubusercontent.com" && pathSegments.size >= 2 -> pathSegments[0] to pathSegments[1]
        else -> return url
    }
    return "https://github.com/$owner/${repository.removeSuffix(".git")}"
}

private const val MAX_REPOSITORY_DISPLAY_NAME_LENGTH = 48
