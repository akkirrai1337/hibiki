package org.akkirrai.hibiki.core.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AniListOAuthTest {
    @Test
    fun `authorization request is client id and implicit grant only`() {
        val request = AniListOAuth.createAuthorizationRequest("51178")

        assertNotNull(request)
        assertEquals(true, request!!.url.contains("client_id=51178"))
        assertEquals(true, request.url.contains("response_type=token"))
        assertEquals(false, request.url.contains("redirect_uri"))
        assertEquals(false, request.url.contains("state="))
    }

    @Test
    fun `callback parses token from redirect fragment`() {
        val callback = AniListOAuth.parseRedirect(
            "hibiki://anilist-auth#access_token=token%2Fvalue&token_type=Bearer&expires_in=31536000&state=expected",
        ) as AniListOAuthCallback.Success

        assertEquals("token/value", callback.accessToken)
        assertEquals(31_536_000L, callback.expiresInSeconds)
        assertEquals("expected", callback.state)
    }

    @Test
    fun `callback preserves OAuth error`() {
        val callback = AniListOAuth.parseRedirect(
            "hibiki://anilist-auth?error=access_denied&error_description=User%20cancelled&state=expected",
        ) as AniListOAuthCallback.Error

        assertEquals("access_denied", callback.code)
        assertEquals("User cancelled", callback.description)
        assertEquals("expected", callback.state)
    }
}
