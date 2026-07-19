package org.akkirrai.hibiki.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserCookieParserTest {
    @Test
    fun `cookie parser preserves values containing equals signs`() {
        assertEquals(
            linkedMapOf(
                "cf_clearance" to "signed=value==",
                "session" to "abc",
            ),
            parseBrowserCookies("cf_clearance=signed=value==; session=abc"),
        )
    }

    @Test
    fun `cookie parser ignores malformed segments`() {
        assertEquals(
            linkedMapOf("valid" to "cookie"),
            parseBrowserCookies("; invalid; =empty-name; valid=cookie"),
        )
    }
}
