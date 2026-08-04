package org.akkirrai.beakokit.api

/** Host-owned cookie jar scoped to one source runtime. */
abstract class SourceHostCookies : SourceHostAccess, SourceHostCookiesAccess {
    override suspend fun forUrl(url: String): Map<String, String> {
        requireUrl(url)
        require(SourceHostCapability.COOKIES)
        requirements.networkPolicy.requireAllowed(url)
        return cookiesForUrl(url).also(::requireCookies)
    }

    override suspend fun storeFromResponse(url: String, cookies: Map<String, String>) {
        requireUrl(url)
        require(SourceHostCapability.COOKIES)
        requirements.networkPolicy.requireAllowed(url)
        requireCookies(cookies)
        storeResponseCookies(url, cookies)
    }

    override suspend fun clear(url: String) {
        requireUrl(url)
        require(SourceHostCapability.COOKIES)
        requirements.networkPolicy.requireAllowed(url)
        clearCookies(url)
    }

    protected abstract suspend fun cookiesForUrl(url: String): Map<String, String>

    protected abstract suspend fun storeResponseCookies(url: String, cookies: Map<String, String>)

    protected abstract suspend fun clearCookies(url: String)

    companion object {
        const val MAX_COOKIE_COUNT: Int = 64
        const val MAX_COOKIE_NAME_LENGTH: Int = 256
        const val MAX_COOKIE_VALUE_LENGTH: Int = 8192

        fun requireUrl(url: String) {
            require(isValidHttpsUrl(url)) { "Cookie URL must be a valid HTTPS URL" }
        }

        fun requireCookies(cookies: Map<String, String>) {
            require(cookies.size <= MAX_COOKIE_COUNT) {
                "Source cookie response contains too many cookies"
            }
            cookies.forEach { (name, value) ->
                require(name.isNotBlank()) { "Cookie name must not be blank" }
                require(name.length <= MAX_COOKIE_NAME_LENGTH) { "Cookie name is too long" }
                require('\r' !in name && '\n' !in name) {
                    "Cookie name must not contain CR or LF"
                }
                require(value.length <= MAX_COOKIE_VALUE_LENGTH) { "Cookie value is too long" }
                require('\r' !in value && '\n' !in value) {
                    "Cookie value must not contain CR or LF"
                }
            }
        }
    }
}
