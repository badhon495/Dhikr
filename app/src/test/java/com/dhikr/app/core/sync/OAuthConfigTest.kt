package com.dhikr.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthConfigTest {

    @Test
    fun blankClientIdYieldsNullConfig() {
        assertNull(oauthConfig(ProviderId.GOOGLE, googleClientId = "", microsoftClientId = "x"))
        assertNull(oauthConfig(ProviderId.MICROSOFT, googleClientId = "x", microsoftClientId = ""))
    }

    @Test
    fun googleConfigUsesReverseClientIdRedirect() {
        val cfg = oauthConfig(
            ProviderId.GOOGLE,
            googleClientId = "123-abc.apps.googleusercontent.com",
            microsoftClientId = "",
        )!!
        assertEquals("com.googleusercontent.apps.123-abc:/oauth2redirect", cfg.redirectUri)
        assertEquals("https://accounts.google.com/o/oauth2/v2/auth", cfg.authEndpoint)
        assertTrue("https://www.googleapis.com/auth/drive.appdata" in cfg.scopes)
    }

    @Test
    fun microsoftConfigUsesConsumersEndpoint() {
        val cfg = oauthConfig(ProviderId.MICROSOFT, googleClientId = "", microsoftClientId = "guid")!!
        assertEquals("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", cfg.tokenEndpoint)
        assertEquals("com.badhon495.dhikr.msauth://auth", cfg.redirectUri)
        assertTrue("Files.ReadWrite.AppFolder" in cfg.scopes)
        assertTrue("offline_access" in cfg.scopes)
    }

    @Test
    fun accountLabelComesFromIdTokenEmail() {
        // payload {"email":"me@example.com"}
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"email":"me@example.com"}""".toByteArray())
        assertEquals("me@example.com", labelFromIdToken("h.$payload.s"))
    }

    @Test
    fun accountLabelFallsBackToPreferredUsernameThenNull() {
        val p = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"preferred_username":"u@live.com"}""".toByteArray())
        assertEquals("u@live.com", labelFromIdToken("h.$p.s"))
        assertNull(labelFromIdToken(null))
        assertNull(labelFromIdToken("garbage"))
    }
}
