# Google Drive Backup/Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add manual "Backup to Drive" / "Restore from Drive" to Settings, reusing the existing local `BackupRepository` JSON format, authenticated with the user's own Google account via Credential Manager, scoped to `drive.file` only.

**Architecture:** New `core/drivesync/` package holds all Drive/auth-specific code, kept separate from the untouched `core/backup/BackupRepository`. `DriveAuthManager` handles Credential Manager sign-in (ID token) plus a separate Authorization step that mints a `drive.file`-scoped access token. `DriveApi` makes the three Drive v3 REST calls (find-by-name, update-or-create, download) over `HttpURLConnection` (no new HTTP client dependency — see Global Constraints). `DriveBackupRepository` composes `DriveAuthManager` + `DriveApi` + the existing `BackupRepository.export()`/`restore()` into backup/restore operations. A new `DriveSyncViewModel` and `DriveSyncControls` composable in `SettingsScreen.kt` expose it, following `BackupViewModel`'s existing `Status` sealed-interface shape.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.credentials` (Credential Manager) + `com.google.android.libraries.identity.googleid` for sign-in, Android's built-in `HttpURLConnection` + `kotlinx.serialization.json` for Drive v3 REST calls, `androidx.security-crypto` (`EncryptedSharedPreferences`, already a dependency) for token storage, `androidx.datastore.preferences` (already a dependency) for the stored Drive file id.

**Spec:** `docs/superpowers/specs/2026-10-23-drive-sync-design.md`

## Global Constraints

- minSdk 24, compileSdk 37, Kotlin 2.3.20, Compose BOM 2026.08.00 (`gradle/libs.versions.toml`).
- Reuse `BackupRepository.export(appVersionName)` / `BackupRepository.restore(content)` unchanged — Drive sync never touches that class's internals, only calls its public API.
- `drive.file` scope only — never request broader Drive scope.
- Do not adopt legacy `GoogleSignInClient`/`GoogleAuthUtil`. Sign-in uses Credential Manager (`GetSignInWithGoogleOption`); Drive-scope authorization uses the Credential Manager companion Authorization API (`androidx.credentials` `AuthorizationClient`, from `play-services-auth` 21.4+ or `credentials` artifacts — confirm current artifact name at implementation time; it is the AndroidX successor, not the deprecated `GoogleSignIn` class).
- Two OAuth client IDs are required from Google Cloud Console (external, one-time setup by the project owner, not part of this plan's tasks): a Web-type client ID (`serverClientId` for sign-in) and an Android-type client ID (package `com.badhon495.dhikr` + release/debug SHA-1, for the Authorization step). Both are read from `BuildConfig` fields populated from `local.properties`, never hardcoded or committed.
- No new HTTP client library — Drive v3 REST calls use `java.net.HttpURLConnection` directly (already implicitly available, zero added APK size) with `kotlinx.serialization.json` for request/response bodies, consistent with build-size constraint (plan.md #55) and this app's existing style (`core/ai/GeminiClient.kt` — check its approach before writing `DriveApi` for the established pattern of making a raw HTTPS JSON call from this codebase).
- Single Drive file (`dhikr_backup.json`) in the signed-in user's regular My Drive (not `appDataFolder`), overwritten in place by file id once known.
- All new strings go in `app/src/main/res/values/strings.xml` as new keys (`settings_backup_drive_*`), following the exact tone of existing `settings_backup_*` keys.
- Manual trigger only — no `WorkManager`, no background service, no automatic backup.
- `PRIVACY_POLICY.md` must be updated in the same branch, modeled on the existing "Optional AI benefits feature" section.

## Review Focus

- **No prior backup exists on this Drive account yet, user taps "Restore from Drive."** Drive file-search returns zero results — must show a clear "no backup found" message, not crash or show a generic error.
- **User revokes the app's Drive access from their Google Account settings (outside the app) between backups.** Next Drive call gets an auth error — silent re-authorization attempt must fail cleanly and fall back to a visible "please sign in again" state, not an infinite retry or an uncaught exception.
- **User signs out, then taps "Backup to Drive" again without re-signing in.** Buttons must be disabled/hidden in signed-out state, not reachable.
- **Network unavailable (airplane mode) when tapping Backup or Restore.** Must show a distinct, worded error (not the generic local-file error strings, which don't apply to a network failure) and leave local data untouched on a failed restore.
- **Backup JSON from Drive is corrupted or truncated (partial upload interrupted previously, or edited by hand in Drive's web UI).** Restore must surface `BackupFormatException`'s message the same way local restore already does — not a raw parse crash.

---

## File Structure

- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveAuthManager.kt` — Credential Manager sign-in/sign-out, Authorization step, signed-in account state.
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveTokenStore.kt` — encrypted at-rest storage for the cached Drive access token + its expiry, mirroring `SecureKeyStore`'s pattern.
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveFileIdStore.kt` — tiny DataStore-backed store for the known Drive file id (so later backups update-in-place).
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveApi.kt` — raw Drive v3 REST calls (find by name, upload/update content, download content) over `HttpURLConnection`.
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveBackupRepository.kt` — composes `DriveAuthManager` + `DriveApi` + `BackupRepository` into `backup()`/`restore()`.
- Create: `app/src/main/java/com/dhikr/app/feature/settings/DriveSyncViewModel.kt` — `Status` sealed interface + sign-in state, drives the UI.
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt` — add `DriveSyncControls` composable inside the existing `settings_backup` section.
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` — wire `DriveAuthManager`/`DriveBackupRepository`/`DriveSyncViewModel.Factory` alongside the existing `BackupRepository`/`BackupViewModel` wiring.
- Modify: `app/src/main/res/values/strings.xml` — new `settings_backup_drive_*` keys.
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml` — add Credential Manager + Google ID + Authorization dependencies.
- Modify: `PRIVACY_POLICY.md` — new section for this feature.
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveApiTest.kt`
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveBackupRepositoryTest.kt`
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveFileIdStoreTest.kt`

---

## Task 1: Dependencies and Drive REST client

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveApi.kt`
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveApiTest.kt`

**Interfaces:**
- Produces: `class DriveApi(private val httpClient: (DriveApi.Request) -> DriveApi.Response = ::defaultHttpCall)` — constructor takes an injectable HTTP call function so tests never hit the network.
  - `data class Request(val method: String, val url: String, val accessToken: String, val body: ByteArray? = null, val contentType: String? = null)`
  - `data class Response(val statusCode: Int, val body: String)`
  - `suspend fun findFileId(accessToken: String, fileName: String): String?` — queries `GET https://www.googleapis.com/drive/v3/files?q=name='<fileName>' and trashed=false&spaces=drive&fields=files(id)`, returns the first file id or null if the `files` array is empty.
  - `suspend fun uploadOrUpdate(accessToken: String, existingFileId: String?, fileName: String, jsonContent: String): String` — if `existingFileId` is null, creates a new file via the Drive v3 multipart upload endpoint (`POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart`) with `mimeType: "application/json"` and returns the new id; if non-null, updates content in place via `PATCH https://www.googleapis.com/upload/drive/v3/files/<id>?uploadType=media` and returns `existingFileId`.
  - `suspend fun downloadContent(accessToken: String, fileId: String): String` — `GET https://www.googleapis.com/drive/v3/files/<id>?alt=media`, returns the raw body text.
  - `class DriveApiException(val statusCode: Int, message: String) : Exception(message)` — thrown by all three methods when `statusCode` is not in 200..299.

- [ ] **Step 1: Add Credential Manager + Google ID + Authorization dependency versions to `gradle/libs.versions.toml`**

Add to `[versions]`: `credentials = "1.6.0"` (or latest stable 1.x at implementation time), `googleid = "1.1.1"`. Add to `[libraries]`: `androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }`, `androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }`, `google-id = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }`. Confirm exact current stable versions and the correct artifact providing the Authorization API (it may be `credentials-play-services-auth` or a newer `androidx.credentials:credentials-exoplayer`-style follow-on artifact — check AndroidX release notes at implementation time) before pinning.

- [ ] **Step 2: Add the three dependencies to `app/build.gradle.kts`'s `dependencies` block**

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.google.id)
```

- [ ] **Step 3: Write failing tests for `DriveApi` in `DriveApiTest.kt`**

```kotlin
@Test
fun `findFileId returns null when files array empty`() = runTest {
    val api = DriveApi(httpClient = { DriveApi.Response(200, """{"files":[]}""") })
    assertNull(api.findFileId("token", "dhikr_backup.json"))
}

@Test
fun `findFileId returns first file id when present`() = runTest {
    val api = DriveApi(httpClient = { DriveApi.Response(200, """{"files":[{"id":"abc123"}]}""") })
    assertEquals("abc123", api.findFileId("token", "dhikr_backup.json"))
}

@Test
fun `findFileId throws DriveApiException on non-2xx`() = runTest {
    val api = DriveApi(httpClient = { DriveApi.Response(401, """{"error":"invalid_token"}""") })
    assertThrows(DriveApi.DriveApiException::class.java) {
        runBlocking { api.findFileId("token", "dhikr_backup.json") }
    }
}

@Test
fun `uploadOrUpdate with null existingFileId creates and returns new id`() = runTest {
    val api = DriveApi(httpClient = { req ->
        assertTrue(req.url.contains("uploadType=multipart"))
        DriveApi.Response(200, """{"id":"newid456"}""")
    })
    val id = api.uploadOrUpdate("token", null, "dhikr_backup.json", "{}")
    assertEquals("newid456", id)
}

@Test
fun `uploadOrUpdate with existingFileId patches and returns same id`() = runTest {
    val api = DriveApi(httpClient = { req ->
        assertTrue(req.url.contains("/files/abc123"))
        assertEquals("PATCH", req.method)
        DriveApi.Response(200, "{}")
    })
    val id = api.uploadOrUpdate("token", "abc123", "dhikr_backup.json", "{}")
    assertEquals("abc123", id)
}

@Test
fun `downloadContent returns raw body`() = runTest {
    val api = DriveApi(httpClient = { DriveApi.Response(200, "raw backup json") })
    assertEquals("raw backup json", api.downloadContent("token", "abc123"))
}

@Test
fun `downloadContent throws on 404`() = runTest {
    val api = DriveApi(httpClient = { DriveApi.Response(404, "") })
    assertThrows(DriveApi.DriveApiException::class.java) {
        runBlocking { api.downloadContent("token", "missing") }
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveApiTest"`
Expected: FAIL (`DriveApi` not defined)

- [ ] **Step 5: Implement `DriveApi` in `DriveApi.kt`**

Use `kotlinx.serialization.json.Json` (already a dependency) to parse `{"files":[{"id":"..."}]}` responses and build the multipart upload body (JSON metadata part + content part, boundary-delimited, per Drive v3's multipart upload format). The injected `httpClient` function is what actually performs the HTTP call in the real (non-test) path — `defaultHttpCall` implements it with `HttpURLConnection`, setting `Authorization: Bearer <token>` and the given method/body/content-type.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveApiTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/dhikr/app/core/drivesync/DriveApi.kt app/src/test/java/com/dhikr/app/core/drivesync/DriveApiTest.kt
git commit -m "feat: add Drive v3 REST client for backup sync"
```

---

## Task 2: Drive file id store

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveFileIdStore.kt`
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveFileIdStoreTest.kt`

**Interfaces:**
- Consumes: none (standalone DataStore wrapper, same pattern as `Context.preferencesDataStore` used in `AppPreferencesRepository.kt`).
- Produces:
  - `class DriveFileIdStore(context: Context)`
  - `suspend fun get(): String?`
  - `suspend fun set(fileId: String)`
  - `suspend fun clear()`

- [ ] **Step 1: Write failing tests in `DriveFileIdStoreTest.kt`**

```kotlin
@Test
fun `get returns null before any set`() = runTest {
    val store = DriveFileIdStore(context)
    assertNull(store.get())
}

@Test
fun `set then get returns the stored id`() = runTest {
    val store = DriveFileIdStore(context)
    store.set("abc123")
    assertEquals("abc123", store.get())
}

@Test
fun `clear removes the stored id`() = runTest {
    val store = DriveFileIdStore(context)
    store.set("abc123")
    store.clear()
    assertNull(store.get())
}
```

Use the same in-memory/test DataStore setup pattern already used for `AppPreferencesRepository`'s existing tests (check `app/src/test/java/com/dhikr/app/core/datastore/` for the established test-context/tmp-dir approach before writing this).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveFileIdStoreTest"`
Expected: FAIL (`DriveFileIdStore` not defined)

- [ ] **Step 3: Implement `DriveFileIdStore` in `DriveFileIdStore.kt`**

A `Context.driveSyncDataStore by preferencesDataStore(name = "drive_sync")` with a single `stringPreferencesKey("drive_file_id")`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveFileIdStoreTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/drivesync/DriveFileIdStore.kt app/src/test/java/com/dhikr/app/core/drivesync/DriveFileIdStoreTest.kt
git commit -m "feat: add Drive file id store"
```

---

## Task 3: Token store

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveTokenStore.kt`

**Interfaces:**
- Consumes: `android.content.Context`
- Produces:
  - `class DriveTokenStore(context: Context)` — mirrors `SecureKeyStore`'s `EncryptedSharedPreferences` construction and corruption-reset pattern exactly (copy the `openResettingOnCorruption`/`deletePrefsFile` approach from `core/ai/SecureKeyStore.kt`, same file name shape, different `PREFS_NAME`).
  - `fun getAccessToken(): String?`
  - `fun getExpiryMillis(): Long?`
  - `suspend fun setToken(accessToken: String, expiryMillis: Long)`
  - `suspend fun clear()`
  - `val isExpired: Boolean get()` — true if no token stored, or `System.currentTimeMillis() >= expiryMillis`.

No dedicated unit test task for this file: `EncryptedSharedPreferences` needs a real Android Keystore, which isn't available in a plain JVM unit test (same reason `SecureKeyStore` itself has no unit test — confirm by checking for its absence in `app/src/test/`). Covered instead by `DriveBackupRepositoryTest` in Task 5 via a fake.

- [ ] **Step 1: Implement `DriveTokenStore` in `DriveTokenStore.kt`**

Copy `SecureKeyStore`'s structure: `PREFS_NAME = "drive_sync_secrets"`, keys `KEY_ACCESS_TOKEN` and `KEY_EXPIRY_MILLIS` (stored as a `Long` via `putLong`/`getLong`).

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/drivesync/DriveTokenStore.kt
git commit -m "feat: add encrypted Drive token store"
```

---

## Task 4: Auth manager (sign-in + authorization)

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveAuthManager.kt`

**Interfaces:**
- Consumes: `DriveTokenStore` (Task 3), `android.content.Context`, `androidx.credentials.CredentialManager`, `com.google.android.libraries.identity.googleid.GoogleIdTokenCredential`, the Authorization API chosen in Task 1.
- Produces:
  - `class DriveAuthManager(private val context: Context, private val tokenStore: DriveTokenStore, private val webClientId: String, private val androidClientId: String)`
  - `data class SignedInAccount(val email: String?, val displayName: String?)`
  - `val signedInAccount: StateFlow<SignedInAccount?>` — null when signed out; restored from a locally-persisted flag/email on construction (Credential Manager itself does not expose "currently signed in" as a standing query — persist the last successful sign-in's account info via `DriveFileIdStore`-style small DataStore entry, or extend `DriveTokenStore` with an email field; pick one and note the choice in the commit).
  - `suspend fun signIn(activityContext: Context): Result<SignedInAccount>` — launches Credential Manager's `GetSignInWithGoogleOption` flow (requires an Activity context), parses the returned `GoogleIdTokenCredential`, updates `signedInAccount`.
  - `suspend fun signOut()` — clears `signedInAccount`, clears `tokenStore`, calls Credential Manager's `clearCredentialState`.
  - `suspend fun getValidAccessToken(activityContext: Context): Result<String>` — if `tokenStore.isExpired` is false, returns the cached token; otherwise runs the Authorization step to mint a fresh `drive.file`-scoped token, stores it via `tokenStore.setToken`, and returns it. On authorization failure, returns `Result.failure` (caller decides whether that means "show sign-in again").

No unit tests for this file: Credential Manager's flows require a real Activity and system UI, not unit-testable in a plain JVM test. `DriveBackupRepository`'s tests (Task 5) use a fake `DriveAuthManager`-shaped interface instead of exercising this class directly — note: extract a minimal interface (`interface DriveTokenProvider { suspend fun getValidAccessToken(activityContext: Context): Result<String> }`) that `DriveAuthManager` implements, so `DriveBackupRepository` depends on the interface and tests can fake it without touching Credential Manager.

- [ ] **Step 1: Define `DriveTokenProvider` interface and implement `DriveAuthManager` against it in `DriveAuthManager.kt`**

Implement `signIn`/`signOut`/`getValidAccessToken` per the Interfaces block above. For the Authorization step inside `getValidAccessToken`, use whichever concrete Authorization API Task 1 settled on; request exactly the `https://www.googleapis.com/auth/drive.file` scope.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/drivesync/DriveAuthManager.kt
git commit -m "feat: add Drive sign-in and authorization manager"
```

---

## Task 5: Drive backup repository

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/drivesync/DriveBackupRepository.kt`
- Test: `app/src/test/java/com/dhikr/app/core/drivesync/DriveBackupRepositoryTest.kt`

**Interfaces:**
- Consumes: `DriveTokenProvider` (Task 4's interface), `DriveApi` (Task 1), `DriveFileIdStore` (Task 2), `BackupRepository.export(appVersionName: String): String` and `BackupRepository.restore(content: String): RestoreResult` (existing, unchanged), `BackupFormatException` (existing).
- Produces:
  - `class DriveBackupRepository(private val tokenProvider: DriveTokenProvider, private val driveApi: DriveApi, private val fileIdStore: DriveFileIdStore, private val backupRepository: BackupRepository, private val appVersionName: String)`
  - `sealed interface BackupOutcome { data class Success(val bytes: Int) : BackupOutcome; data class AuthFailed(val cause: Throwable) : BackupOutcome; data class NetworkFailed(val cause: Throwable) : BackupOutcome }`
  - `sealed interface RestoreOutcome { data class Success(val result: RestoreResult) : RestoreOutcome; data object NoBackupFound : RestoreOutcome; data class AuthFailed(val cause: Throwable) : RestoreOutcome; data class NetworkFailed(val cause: Throwable) : RestoreOutcome; data class FormatInvalid(val message: String) : RestoreOutcome }`
  - `suspend fun backup(activityContext: Context): BackupOutcome` — gets a valid token; on failure returns `AuthFailed`. Builds JSON via `backupRepository.export(appVersionName)`. Looks up `fileIdStore.get()`; if null, calls `driveApi.findFileId` first (covers the case where a file from a previous install/session exists on Drive but the local id cache was cleared) and stores whatever id is found or created via `fileIdStore.set`. Calls `driveApi.uploadOrUpdate`. Wraps `DriveApi.DriveApiException`/`IOException` into `NetworkFailed` or `AuthFailed` (401/403 status → `AuthFailed`, everything else → `NetworkFailed`).
  - `suspend fun restore(activityContext: Context): RestoreOutcome` — gets a valid token; resolves the file id the same way `backup` does (stored id, else `findFileId`); if no id found anywhere, returns `NoBackupFound`. Downloads content via `driveApi.downloadContent`, passes it to `backupRepository.restore(content)`, catches `BackupFormatException` into `FormatInvalid`, other failures into `AuthFailed`/`NetworkFailed` by the same status-code rule as `backup`.

- [ ] **Step 1: Write failing tests in `DriveBackupRepositoryTest.kt`**

```kotlin
private class FakeTokenProvider(private val result: Result<String>) : DriveTokenProvider {
    override suspend fun getValidAccessToken(activityContext: Context) = result
}

@Test
fun `backup with no cached file id searches by name then uploads as new`() = runTest {
    val fileIdStore = FakeDriveFileIdStore()
    val api = FakeDriveApi(findFileIdResult = null, uploadOrUpdateResult = "newid")
    val repo = DriveBackupRepository(FakeTokenProvider(Result.success("tok")), api, fileIdStore, fakeBackupRepository, "1.0")
    val outcome = repo.backup(context)
    assertTrue(outcome is DriveBackupRepository.BackupOutcome.Success)
    assertEquals("newid", fileIdStore.get())
}

@Test
fun `backup with cached file id updates existing file directly, skips search`() = runTest {
    val fileIdStore = FakeDriveFileIdStore().also { it.set("existing") }
    val api = FakeDriveApi(findFileIdResult = null, uploadOrUpdateResult = "existing")
    val repo = DriveBackupRepository(FakeTokenProvider(Result.success("tok")), api, fileIdStore, fakeBackupRepository, "1.0")
    repo.backup(context)
    assertFalse(api.findFileIdCalled)
}

@Test
fun `backup returns AuthFailed when token provider fails`() = runTest {
    val repo = DriveBackupRepository(FakeTokenProvider(Result.failure(Exception("no auth"))), FakeDriveApi(), FakeDriveFileIdStore(), fakeBackupRepository, "1.0")
    val outcome = repo.backup(context)
    assertTrue(outcome is DriveBackupRepository.BackupOutcome.AuthFailed)
}

@Test
fun `restore returns NoBackupFound when no cached id and search finds nothing`() = runTest {
    val api = FakeDriveApi(findFileIdResult = null)
    val repo = DriveBackupRepository(FakeTokenProvider(Result.success("tok")), api, FakeDriveFileIdStore(), fakeBackupRepository, "1.0")
    val outcome = repo.restore(context)
    assertEquals(DriveBackupRepository.RestoreOutcome.NoBackupFound, outcome)
}

@Test
fun `restore returns FormatInvalid when downloaded content isn't a valid backup`() = runTest {
    val fileIdStore = FakeDriveFileIdStore().also { it.set("id1") }
    val api = FakeDriveApi(downloadContentResult = "not json")
    val repo = DriveBackupRepository(FakeTokenProvider(Result.success("tok")), api, fileIdStore, realBackupRepositoryThatThrowsOnBadFormat, "1.0")
    val outcome = repo.restore(context)
    assertTrue(outcome is DriveBackupRepository.RestoreOutcome.FormatInvalid)
}

@Test
fun `restore returns Success and applies the downloaded backup`() = runTest {
    val fileIdStore = FakeDriveFileIdStore().also { it.set("id1") }
    val api = FakeDriveApi(downloadContentResult = validBackupJson)
    val repo = DriveBackupRepository(FakeTokenProvider(Result.success("tok")), api, fileIdStore, fakeBackupRepository, "1.0")
    val outcome = repo.restore(context)
    assertTrue(outcome is DriveBackupRepository.RestoreOutcome.Success)
}
```

Build `FakeDriveApi` (implements the same calls `DriveApi` exposes, configurable per-test results/flags) and `FakeDriveFileIdStore` (in-memory) as test-local fakes in this file. Use the real `BackupRepository` wired to an in-memory/fake `AppDatabase` the way existing `BackupRepository` tests already do (check `app/src/test/java/com/dhikr/app/core/backup/BackupRepositoryTest.kt` for that setup and reuse it) for `fakeBackupRepository`/`realBackupRepositoryThatThrowsOnBadFormat` rather than mocking `BackupRepository` itself.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveBackupRepositoryTest"`
Expected: FAIL (`DriveBackupRepository` not defined)

- [ ] **Step 3: Implement `DriveBackupRepository` in `DriveBackupRepository.kt`**

Per the Interfaces block's described behavior.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dhikr.app.core.drivesync.DriveBackupRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/drivesync/DriveBackupRepository.kt app/src/test/java/com/dhikr/app/core/drivesync/DriveBackupRepositoryTest.kt
git commit -m "feat: add Drive backup repository"
```

---

## Task 6: ViewModel

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/settings/DriveSyncViewModel.kt`

**Interfaces:**
- Consumes: `DriveAuthManager` (Task 4, concrete class — needed for `signIn`/`signOut`/`signedInAccount`, not just the `DriveTokenProvider` interface), `DriveBackupRepository` (Task 5).
- Produces:
  - `class DriveSyncViewModel(private val authManager: DriveAuthManager, private val backupRepository: DriveBackupRepository) : ViewModel()`
  - `sealed interface Status { data object Idle : Status; data object Working : Status; data class BackupDone(val bytes: Int) : Status; data class RestoreDone(val result: RestoreResult) : Status; data object NoBackupFound : Status; data class Error(val message: String) : Status }` — mirrors `BackupViewModel.Status`'s shape per the spec's instruction to reuse that vocabulary.
  - `val signedInAccount: StateFlow<DriveAuthManager.SignedInAccount?>` — forwards `authManager.signedInAccount`.
  - `val status: StateFlow<Status>`
  - `fun clearStatus()`
  - `fun signIn(activityContext: Context)` — calls `authManager.signIn`, sets `Status.Error` on failure.
  - `fun signOut()`
  - `fun backup(activityContext: Context)` — guards on `Status.Working` the same way `BackupViewModel.export` does; maps `DriveBackupRepository.BackupOutcome` to `Status`.
  - `fun restore(activityContext: Context)` — maps `DriveBackupRepository.RestoreOutcome` to `Status`, including `NoBackupFound`.
  - `class Factory(private val authManager: DriveAuthManager, private val backupRepository: DriveBackupRepository) : ViewModelProvider.Factory`

Error message mapping (exact strings deferred to Task 8's `strings.xml`; this task wires `Status.Error` with a string-resource-ready reason code or calls a passed-in string lookup — follow whichever pattern `BackupViewModel` uses for turning an outcome into a displayed message, i.e. keep the mapping in the Composable layer via `when` on `Status`, same as `SettingsScreen.kt`'s existing `when (val s = status)` block for `BackupViewModel.Status`, not inside the ViewModel).

- [ ] **Step 1: Implement `DriveSyncViewModel` in `DriveSyncViewModel.kt`**

Follow `BackupViewModel.kt`'s exact structure (guard on `Working`, `viewModelScope.launch`, `withContext(Dispatchers.IO)` around the repository call, `Factory` pattern) — this is a close structural copy with Drive-specific outcome types.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/settings/DriveSyncViewModel.kt
git commit -m "feat: add Drive sync view model"
```

---

## Task 7: Settings UI

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `DriveSyncViewModel` (Task 6), `DriveAuthManager.SignedInAccount` (Task 4).
- Produces: `DriveSyncControls(viewModel: DriveSyncViewModel)` composable, placed in `SettingsScreen.kt`'s existing `SettingsSection(stringResource(R.string.settings_backup))` block (`SettingsScreen.kt:380-382`), directly below the existing `BackupControls(backupViewModel)` call.

- [ ] **Step 1: Add new string resources to `strings.xml`**

Add alongside the existing `settings_backup_*` block (`strings.xml:302-312`): `settings_backup_drive_title` ("Backup to Google Drive"), `settings_backup_drive_desc` (one sentence: backs up the same data to your own Google Drive, signed in with your own account), `settings_backup_drive_sign_in` ("Sign in with Google"), `settings_backup_drive_sign_out` ("Sign out"), `settings_backup_drive_signed_in_as` ("Signed in as %1$s"), `settings_backup_drive_backup` ("Backup to Drive"), `settings_backup_drive_restore` ("Restore from Drive"), `settings_backup_drive_working` ("Working…"), `settings_backup_drive_backup_done` ("Backed up to Drive."), `settings_backup_drive_restore_done` (reuse the `settings_backup_restore_done`/`_skipped` format strings — same %1$d/%2$d/%3$d/%4$d shape, new key names `settings_backup_drive_restore_done`/`settings_backup_drive_restore_done_skipped`), `settings_backup_drive_no_backup_found` ("No backup found on this Google account yet."), `settings_backup_drive_error_auth` ("Couldn't verify your Google account. Try signing in again."), `settings_backup_drive_error_network` ("No connection. Check your internet and try again.").

- [ ] **Step 2: Wire `DriveAuthManager`, `DriveBackupRepository`, and `DriveSyncViewModel.Factory` in `DhikrApp.kt`**

Alongside the existing `backupRepository`/`backupViewModel` wiring (`DhikrApp.kt:502,511-512`): construct `DriveTokenStore`, `DriveFileIdStore`, `DriveApi`, `DriveAuthManager` (with the `webClientId`/`androidClientId` read from `BuildConfig`), `DriveBackupRepository` (reusing the already-constructed `backupRepository`), and `DriveSyncViewModel` via `viewModel(factory = ...)`.

- [ ] **Step 3: Implement `DriveSyncControls` in `SettingsScreen.kt`**

Follow `BackupControls`'s structure (`SettingsScreen.kt:940` on): collect `viewModel.signedInAccount` and `viewModel.status` as state. When `signedInAccount` is null, show the description text plus a single "Sign in with Google" `BackupActionRow` calling `viewModel.signIn(context)` (reuse `LocalContext.current`, cast to Activity where Credential Manager's API requires one — follow whatever pattern this call needs; Credential Manager APIs take any `Context` for `CredentialManager.create(context)` but the sign-in request itself is launched via `credentialManager.getCredential(activityContext, request)`, which needs an Activity context, consistent with Task 4's `activityContext: Context` parameter naming). When signed in, show the account email (`settings_backup_drive_signed_in_as`), then "Backup to Drive", "Restore from Drive", and "Sign out" rows, each disabled while `status is Status.Working`. Map `Status` to a message `Text` exactly like `BackupControls`'s existing `when (val s = status)` block, using the new string keys from Step 1.

- [ ] **Step 4: Manually verify the Settings screen**

Run the app, open Settings → Backup section, confirm the new Drive controls render below the existing local backup controls, in both signed-out and (if a test Google account / OAuth client is available) signed-in states.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt app/src/main/java/com/dhikr/app/DhikrApp.kt app/src/main/res/values/strings.xml
git commit -m "feat: add Drive sync controls to Settings"
```

---

## Task 8: Privacy policy update

**Files:**
- Modify: `PRIVACY_POLICY.md`

- [ ] **Step 1: Add a new section to `PRIVACY_POLICY.md`, modeled on the existing "Optional AI benefits feature" section**

Add a section titled "Optional Google Drive backup" after "Optional AI benefits feature", stating: off by default; requires the user to explicitly sign in with their own Google account; the app requests only the `drive.file` scope (it can only see files the app itself creates, never the rest of the user's Drive); when the user taps "Backup to Drive," the same backup content described in "Data you choose to export" is sent directly from the device to the user's own Google Drive (not to us); signing out removes the app's local credential but does not delete the Drive file; update the "Permissions the app requests" `INTERNET` row to also mention this feature as a second reason the app uses the network.

- [ ] **Step 2: Commit**

```bash
git add PRIVACY_POLICY.md
git commit -m "docs: document optional Google Drive backup in privacy policy"
```

---

## Task 9: Whole-feature manual verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass including the new `drivesync` tests.

- [ ] **Step 2: Manually verify the five Review Focus scenarios**

Using a real or test Google account with the Cloud Console OAuth clients configured (prerequisite, external to this plan): (1) restore with no prior backup shows "no backup found"; (2) revoke app access from Google Account settings, then attempt backup/restore, confirm clean re-sign-in prompt rather than a crash; (3) sign out, confirm Drive action rows are gone/disabled; (4) airplane mode, attempt backup, confirm the network-specific error message and that local data is unchanged after a failed restore attempt; (5) manually edit the Drive file to invalid JSON via Drive's web UI, attempt restore, confirm the format-invalid message appears.

- [ ] **Step 3: Commit if any fixes were needed during verification**

```bash
git add -A
git commit -m "fix: address issues found in Drive sync manual verification"
```
