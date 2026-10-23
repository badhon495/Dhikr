package com.dhikr.app.core.drivesync

import android.accounts.Account
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The Drive scope this app ever requests: per-file access to items it creates
 *  itself, never full Drive read/write. */
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** The account currently signed in for Drive sync, or null when signed out. */
data class SignedInAccount(val email: String?, val displayName: String?)

/**
 * Narrow interface for callers (e.g. `DriveBackupRepository`) that only need a
 * valid access token and shouldn't depend on — or have to fake — the full
 * Credential Manager sign-in surface. [DriveAuthManager] is the only
 * implementation; tests fake this interface instead of exercising Credential
 * Manager directly, which requires a real Activity and system UI.
 */
interface DriveTokenProvider {
    suspend fun getValidAccessToken(activityContext: Context): Result<String>

    /** Clears any cached access token so the next [getValidAccessToken] call
     *  mints a fresh one instead of reusing one the server has rejected
     *  (e.g. after a 401/403 from a Drive API call). */
    suspend fun invalidate()
}

/**
 * Owns Google sign-in (via Credential Manager) and Drive authorization (via
 * the Identity Services Authorization API) for Drive backup sync.
 *
 * These are two distinct steps, deliberately: Credential Manager's
 * `GetSignInWithGoogleOption` flow authenticates *who* the user is (identity,
 * email, display name) and hands back a `GoogleIdTokenCredential` — it never
 * mints an OAuth access token scoped to a Google API. Minting a
 * `drive.file`-scoped access token is a separate authorization step, done via
 * `Identity.getAuthorizationClient` (`com.google.android.gms.auth.api.identity`,
 * transitively provided by `androidx.credentials:credentials-play-services-auth`).
 * This is Google's current, non-deprecated Authorization API; the legacy
 * `GoogleSignInClient`/`GoogleSignInOptions` surface is not used anywhere in
 * this class.
 *
 * Credential Manager has no standing "am I signed in" query — [signedInAccount]
 * is restored on construction from a small DataStore entry (reusing the same
 * `drive_sync` [androidx.datastore.preferences.preferencesDataStore] that
 * [DriveFileIdStore] uses) rather than extending [DriveTokenStore], so Task 3's
 * already-committed encrypted-token-only interface stays untouched.
 */
class DriveAuthManager(
    private val context: Context,
    private val tokenStore: DriveTokenStore,
    private val fileIdStore: DriveFileIdStore,
    private val webClientId: String,
    // Android-type OAuth client ids are matched automatically by Google via
    // the app's package name + signing SHA-1, never passed explicitly in
    // code. Kept as a constructor parameter (rather than removed) so the
    // call site mirrors both BuildConfig fields symmetrically and the field
    // is available if a future API surface needs it explicitly.
    @Suppress("unused") private val androidClientId: String,
) : DriveTokenProvider {

    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _signedInAccount = MutableStateFlow<SignedInAccount?>(null)
    val signedInAccount: StateFlow<SignedInAccount?> = _signedInAccount.asStateFlow()

    init {
        restoreScope.launch {
            val prefs = appContext.driveSyncDataStore.data.first()
            val email = prefs[KEY_ACCOUNT_EMAIL]
            val displayName = prefs[KEY_ACCOUNT_DISPLAY_NAME]
            if (email != null || displayName != null) {
                _signedInAccount.value = SignedInAccount(email = email, displayName = displayName)
            }
        }
    }

    /**
     * Launches Credential Manager's Sign in with Google flow. Requires an
     * Activity-backed [activityContext] since it shows system UI.
     */
    suspend fun signIn(activityContext: Context): Result<SignedInAccount> {
        return try {
            val nonce = UUID.randomUUID().toString()
            // GetSignInWithGoogleOption.Builder validates webClientId is
            // non-empty and throws synchronously (not a GetCredentialException)
            // when it's blank, e.g. before the project owner has configured
            // real Google Cloud Console OAuth client ids. The broad catch
            // below covers this and any other unmapped exception from this
            // whole flow.
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(nonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val response = credentialManager.getCredential(activityContext, request)
            val account = parseSignedInAccount(response.credential)
                ?: return Result.failure(IllegalStateException("Unexpected credential type returned by Credential Manager"))
            persistAccount(account)
            _signedInAccount.value = account
            Result.success(account)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Clears the cached Drive access token, forcing the next
     *  [getValidAccessToken] call to re-authorize instead of reusing a token
     *  the server has already rejected (401/403). */
    override suspend fun invalidate() {
        tokenStore.clear()
    }

    /** Clears local sign-in state, the cached Drive token, and Credential
     *  Manager's own credential state for this app. */
    suspend fun signOut() {
        _signedInAccount.value = null
        tokenStore.clear()
        fileIdStore.clear()
        persistAccount(null)
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            // Best-effort: local state is already cleared above either way.
        }
    }

    /**
     * Returns a `drive.file`-scoped access token, reusing the cached one from
     * [tokenStore] while it's still valid and otherwise running the
     * Authorization step to mint a fresh one.
     */
    override suspend fun getValidAccessToken(activityContext: Context): Result<String> {
        val cached = tokenStore.getAccessToken()
        if (cached != null && !tokenStore.isExpired) {
            return Result.success(cached)
        }
        return authorizeDriveFileScope(activityContext)
    }

    private suspend fun authorizeDriveFileScope(activityContext: Context): Result<String> {
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        // Pin authorization to the signed-in account on a multi-Google-account
        // device, so this step can't silently authorize a different account
        // than the one shown as "Signed in as X". setAccount(Account) is
        // available on this project's play-services-auth version (21.1.1).
        _signedInAccount.value?.email?.let { email ->
            requestBuilder.setAccount(Account(email, "com.google"))
        }
        val request = requestBuilder.build()

        return try {
            val result = awaitAuthorization(activityContext, request)
            if (result.hasResolution()) {
                // First-time consent for this scope: a PendingIntent must be
                // launched from an Activity's ActivityResultLauncher to show
                // the consent screen, which this plain class cannot do on its
                // own. Surface it so a UI layer can launch it and retry.
                return Result.failure(
                    DriveAuthorizationResolutionRequired(result.pendingIntent),
                )
            }
            val accessToken = result.accessToken
                ?: return Result.failure(IllegalStateException("Authorization succeeded but returned no access token"))
            // The Authorization API doesn't report an expiry; Drive access
            // tokens are short-lived (~1 hour), so cache conservatively.
            val expiryMillis = System.currentTimeMillis() + ACCESS_TOKEN_TTL_MILLIS
            tokenStore.setToken(accessToken, expiryMillis)
            Result.success(accessToken)
        } catch (e: ApiException) {
            Result.failure(e)
        }
    }

    private suspend fun awaitAuthorization(
        activityContext: Context,
        request: AuthorizationRequest,
    ): AuthorizationResult = suspendCancellableCoroutine { continuation ->
        val task: Task<AuthorizationResult> =
            Identity.getAuthorizationClient(activityContext).authorize(request)
        task.addOnSuccessListener { result -> continuation.resume(result) }
        task.addOnFailureListener { exception -> continuation.resumeWithException(exception) }
        task.addOnCanceledListener {
            continuation.resumeWithException(java.util.concurrent.CancellationException("Authorization cancelled"))
        }
    }

    private fun parseSignedInAccount(credential: Credential): SignedInAccount? {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return null
        }
        val idTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return SignedInAccount(
            email = extractEmailClaim(idTokenCredential.idToken),
            displayName = idTokenCredential.displayName,
        )
    }

    /** [GoogleIdTokenCredential] exposes the display name but not the email
     *  directly; the email is a standard claim inside the ID token's JWT
     *  payload. Decoded for display only — the payload's signature is not
     *  re-verified here since this value never gates access (the actual Drive
     *  access token is minted and verified by Google's Authorization API). */
    private fun extractEmailClaim(idToken: String): String? = try {
        val payload = idToken.split(".").getOrNull(1)
        if (payload == null) {
            null
        } else {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            if (json.has("email")) json.getString("email") else null
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: org.json.JSONException) {
        null
    }

    /** True when [e] represents the user backing out of the Credential
     *  Manager sign-in dialog rather than a real failure, so callers can map
     *  it to an idle/no-op state instead of showing an error. */
    fun isSignInCancellation(e: Throwable): Boolean = e is GetCredentialCancellationException

    private suspend fun persistAccount(account: SignedInAccount?) = withContext(Dispatchers.IO) {
        appContext.driveSyncDataStore.edit { prefs ->
            if (account?.email != null) {
                prefs[KEY_ACCOUNT_EMAIL] = account.email
            } else {
                prefs.remove(KEY_ACCOUNT_EMAIL)
            }
            if (account?.displayName != null) {
                prefs[KEY_ACCOUNT_DISPLAY_NAME] = account.displayName
            } else {
                prefs.remove(KEY_ACCOUNT_DISPLAY_NAME)
            }
        }
    }

    private companion object {
        val KEY_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val KEY_ACCOUNT_DISPLAY_NAME = stringPreferencesKey("drive_account_display_name")
        const val ACCESS_TOKEN_TTL_MILLIS = 55L * 60L * 1000L // 55 minutes; Drive tokens last ~1 hour.
    }
}

/**
 * Thrown (wrapped in [Result.failure]) when the Authorization API requires
 * user consent UI before it can mint a `drive.file` token. [pendingIntent]
 * must be launched by an Activity (e.g. via
 * `ActivityResultContracts.StartIntentSenderForResult`); on success, retry
 * [DriveAuthManager.getValidAccessToken].
 */
class DriveAuthorizationResolutionRequired(
    val pendingIntent: android.app.PendingIntent?,
) : Exception("Drive authorization requires user consent")
