package org.akkirrai.beakokit.api

import java.net.URI

/**
 * A browser session requested by a source when ordinary HTTP is blocked by an interactive
 * verification page. The host decides how the browser is presented to the user.
 */
data class ChallengeSessionRequest(
    val url: String,
    val requiredCookieNames: Set<String>,
    val forceRefresh: Boolean = false,
) {
    init {
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "Challenge URL must be an absolute HTTPS URL"
        }
        require(requiredCookieNames.isNotEmpty()) { "At least one completion cookie is required" }
        require(requiredCookieNames.all(COOKIE_NAME::matches)) { "Completion cookie names are invalid" }
    }

    private companion object {
        val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    }
}

data class ChallengeSession(
    val cookies: Map<String, String>,
    val userAgent: String,
) {
    init {
        require(cookies.isNotEmpty()) { "A challenge session must contain cookies" }
        require(userAgent.isNotBlank()) { "A challenge session must contain a user agent" }
        require(cookies.keys.all { it.isNotBlank() && '=' !in it && ';' !in it }) {
            "A challenge session contains an invalid cookie name"
        }
        require(cookies.values.none { '\r' in it || '\n' in it }) {
            "A challenge session contains an invalid cookie value"
        }
        require('\r' !in userAgent && '\n' !in userAgent) { "A challenge session contains an invalid user agent" }
    }

    val cookieHeader: String
        get() = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

    fun headers(): Map<String, String> = mapOf(
        "Cookie" to cookieHeader,
        "User-Agent" to userAgent,
    )
}

fun interface ChallengeSessionProvider {
    suspend fun acquire(request: ChallengeSessionRequest): ChallengeSession

    companion object {
        val UNSUPPORTED = ChallengeSessionProvider {
            throw SourceUnavailableException(
                "This source requires an interactive browser session, but the host does not provide one",
            )
        }
    }
}
