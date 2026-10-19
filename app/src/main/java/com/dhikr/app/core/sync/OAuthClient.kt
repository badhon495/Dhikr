package com.dhikr.app.core.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dhikr.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume

data class OAuthProviderConfig(
    val authEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
)

private const val GOOGLE_SUFFIX = ".apps.googleusercontent.com"

/** Null when the provider's client ID isn't configured (see docs/cloud-sync-setup.md). */
fun oauthConfig(
    provider: ProviderId,
    googleClientId: String = BuildConfig.GOOGLE_CLIENT_ID,
    microsoftClientId: String = BuildConfig.MICROSOFT_CLIENT_ID,
): OAuthProviderConfig? = when (provider) {
    ProviderId.GOOGLE -> googleClientId.takeIf { it.isNotBlank() }?.let { id ->
        OAuthProviderConfig(
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            clientId = id,
            redirectUri = "com.googleusercontent.apps.${id.removeSuffix(GOOGLE_SUFFIX)}:/oauth2redirect",
            scopes = listOf("https://www.googleapis.com/auth/drive.appdata", "openid", "email"),
        )
    }
    ProviderId.MICROSOFT -> microsoftClientId.takeIf { it.isNotBlank() }?.let { id ->
        OAuthProviderConfig(
            authEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
            tokenEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            clientId = id,
            redirectUri = "com.badhon495.dhikr.msauth://auth",
            scopes = listOf("Files.ReadWrite.AppFolder", "offline_access", "openid", "email", "profile"),
        )
    }
}

/** Email / username claim from an OIDC id_token, for showing "signed in as …". */
fun labelFromIdToken(idToken: String?): String? {
    val payload = idToken?.split('.')?.getOrNull(1) ?: return null
    return try {
        val bytes = java.util.Base64.getUrlDecoder().decode(payload)
        val obj = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
        obj.stringClaim("email") ?: obj.stringClaim("preferred_username")
    } catch (e: Exception) {
        null
    }
}

private fun JsonObject.stringClaim(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

/** AppAuth wrapper: start sign-in, finish it, and hand out fresh access tokens. */
class OAuthClient(
    context: Context,
    private val tokens: SyncTokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext

    fun isConfigured(provider: ProviderId): Boolean = oauthConfig(provider) != null

    /** Intent to launch for the browser sign-in; null if the provider isn't configured. */
    fun authorizationIntent(provider: ProviderId): Intent? {
        val cfg = oauthConfig(provider) ?: return null
        val request = AuthorizationRequest.Builder(
            serviceConfig(cfg), cfg.clientId, ResponseTypeValues.CODE, Uri.parse(cfg.redirectUri),
        )
            .setScope(cfg.scopes.joinToString(" "))
            .apply {
                if (provider == ProviderId.GOOGLE) {
                    // Without these Google issues no refresh token and sync would need re-login hourly.
                    setAdditionalParameters(mapOf("access_type" to "offline", "prompt" to "consent"))
                }
            }
            .build()
        val service = AuthorizationService(appContext)
        try {
            return service.getAuthorizationRequestIntent(request)
        } finally {
            service.dispose()
        }
    }

    /** Finishes sign-in from the redirect [data]; returns the account label (email) or a generic name. */
    suspend fun completeSignIn(provider: ProviderId, data: Intent): String {
        val cfg = oauthConfig(provider) ?: throw SyncException(SyncException.Kind.NOT_CONNECTED, "Provider not set up")
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (response == null) {
            val cancelled = error?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code
            throw SyncException(
                if (cancelled) SyncException.Kind.NOT_CONNECTED else SyncException.Kind.UNKNOWN,
                error?.errorDescription ?: "Sign-in failed",
                error,
            )
        }
        val token = performToken(response.createTokenExchangeRequest())
        val access = token.accessToken ?: throw SyncException(SyncException.Kind.UNKNOWN, "No access token")
        val refresh = token.refreshToken
            ?: throw SyncException(SyncException.Kind.UNKNOWN, "Provider did not return a refresh token")
        tokens.save(provider, refresh, access, token.accessTokenExpirationTime ?: (clock() + 3_000_000L))
        return labelFromIdToken(token.idToken)
            ?: if (provider == ProviderId.GOOGLE) "Google Drive" else "OneDrive"
    }

    /** A valid access token, refreshing it if it expires within a minute. */
    suspend fun freshAccessToken(provider: ProviderId): String {
        val cfg = oauthConfig(provider) ?: throw SyncException(SyncException.Kind.NOT_CONNECTED, "Provider not set up")
        tokens.accessToken(provider)?.let { (token, expiresAt) ->
            if (expiresAt - clock() > 60_000L) return token
        }
        val refresh = tokens.refreshToken(provider)
            ?: throw SyncException(SyncException.Kind.NOT_CONNECTED, "Not connected")
        val request = TokenRequest.Builder(serviceConfig(cfg), cfg.clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refresh)
            .build()
        val token = try {
            performToken(request)
        } catch (e: SyncException) {
            if (e.kind == SyncException.Kind.AUTH_EXPIRED) tokens.clear(provider)
            throw e
        }
        val access = token.accessToken ?: throw SyncException(SyncException.Kind.UNKNOWN, "No access token")
        tokens.save(provider, token.refreshToken, access, token.accessTokenExpirationTime ?: (clock() + 3_000_000L))
        return access
    }

    private fun serviceConfig(cfg: OAuthProviderConfig) =
        AuthorizationServiceConfiguration(Uri.parse(cfg.authEndpoint), Uri.parse(cfg.tokenEndpoint))

    private suspend fun performToken(request: TokenRequest): TokenResponse {
        val service = AuthorizationService(appContext)
        try {
            return suspendCancellableCoroutine { cont ->
                service.performTokenRequest(request) { response, ex ->
                    if (response != null) {
                        cont.resume(response)
                    } else {
                        val kind = when {
                            ex?.error == "invalid_grant" -> SyncException.Kind.AUTH_EXPIRED
                            ex?.type == AuthorizationException.TYPE_GENERAL_ERROR -> SyncException.Kind.NETWORK
                            else -> SyncException.Kind.UNKNOWN
                        }
                        cont.resumeWith(Result.failure(SyncException(kind, ex?.errorDescription ?: "Token request failed", ex)))
                    }
                }
            }
        } finally {
            service.dispose()
        }
    }
}
