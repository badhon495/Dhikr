# Cloud Sync (Google Drive / OneDrive) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Sync now" button in Settings that two-way syncs custom tasbih, custom routines, built-in favourites, session history and the daily goal through the user's own Google Drive or OneDrive app folder.

**Architecture:** A pure three-way merge (local vs remote vs saved base) in `core/sync`, driven by a `SyncEngine` that talks to a `CloudProvider` interface and a `SyncLocal` interface (Room/DataStore). Two thin HTTP providers authenticate with AppAuth (OAuth2 + PKCE). No Room schema change.

**Tech Stack:** Kotlin, Room, kotlinx-serialization, AppAuth-Android (`net.openid:appauth`), `HttpURLConnection` (like `GeminiClient`), EncryptedSharedPreferences (like `SecureKeyStore`), JUnit4 + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-19-cloud-sync-design.md` (read the **REVISION** section; it supersedes the earlier tombstone/migration text).

## Global Constraints

- Database stays at version 29 with **no schema change**; never add entities/columns for sync.
- Only `INTERNET` permission; no analytics; data leaves the device only on a user tap of Sync now.
- Remote file name `dhikr-sync.json`; format string `"dhikr.sync"`, version `1`; a file with a higher version is rejected and local data is untouched.
- Scopes: Google `https://www.googleapis.com/auth/drive.appdata`; Microsoft `Files.ReadWrite.AppFolder offline_access`.
- Tokens: encrypted store only; `sync_state` prefs hold no secrets.
- Session identity key: `"$tasbihId|$startedAt|$endedAt|$count"`.
- App must build and run with no client IDs configured (`local.properties` keys `google.clientId`, `microsoft.clientId` absent): provider rows show "Not set up yet", Connect disabled.
- Strings in both `values/strings.xml` and `values-bn/strings.xml`.
- Verify with `gradlew.bat :app:testDebugUnitTest` and `gradlew.bat :app:assembleDebug` (project rule: build, don't launch the emulator).
- Commit message trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- Pressing Sync now twice quickly: second call must be ignored/rejected, not run concurrently (Task 3 test).
- Remote file corrupt or from a newer app version: error shown, local data and base untouched (Task 3 tests).
- Upload fails (offline/quota) after local apply: base file must NOT be updated, so retrying is safe and idempotent (Task 3 test).
- Remote sessions that reference a tasbih id this device doesn't have: skipped locally but kept in the remote file (Task 2 + Task 1 tests).
- Deleting a tasbih on one phone while a routine on the other phone still uses it: tasbih is kept, never FK-crashes (Task 1 test).
- First sync on a device that already has lots of data against an empty remote: pure upload, nothing deleted (Task 1 test).

---

## File Structure

- Create `core/sync/SyncMerge.kt` — pure merge + session key + session diff.
- Create `core/sync/SyncCodec.kt` — encode/decode remote file, format/version checks.
- Create `core/sync/SyncEngine.kt` — orchestration, interfaces `CloudProvider`, `SyncLocal`, `SyncFiles`, `SyncException`.
- Create `core/sync/RoomSyncLocal.kt` — `SyncLocal` over Room + DataStore.
- Create `core/sync/FileSyncFiles.kt` — `SyncFiles` over `filesDir/sync/`.
- Create `core/sync/SyncStateStore.kt`, `core/sync/SyncTokenStore.kt`.
- Create `core/sync/OAuthClient.kt` — AppAuth wrapper (authorize intent, token exchange, refresh).
- Create `core/sync/GoogleDriveProvider.kt`, `core/sync/OneDriveProvider.kt`.
- Create `feature/settings/SyncViewModel.kt`; modify `feature/settings/SettingsScreen.kt` (new section), `DhikrApp.kt` (wiring).
- Modify `core/backup/BackupRepository.kt` (extract `buildFile`), `core/database/dao/SessionDao.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `res/xml/*backup*` rules, strings, docs.
- Tests under `app/src/test/java/com/dhikr/app/core/sync/`.

(All paths relative to `app/src/main/java/com/dhikr/app/` unless starting with `app/`, `gradle/`, `docs/`.)

---

### Task 1: Pure merge logic

**Files:**
- Create: `core/sync/SyncMerge.kt`
- Test: `app/src/test/java/com/dhikr/app/core/sync/SyncMergeTest.kt`

**Interfaces:**
- Produces:
  - `fun sessionKey(s: BackupSession): String`
  - `data class MergeResult(val merged: BackupFile, val localChanges: Int, val remoteChanges: Int)`
  - `fun mergeState(local: BackupFile, remote: BackupFile?, base: BackupFile?): MergeResult` — `remote == null` means no remote file yet; `base == null` means no previous sync. Returned `merged.preferences` carries only `dailyGoalTarget`. Counts: `localChanges` = tasbih/routines/favourites/sessions that differ between merged and local; `remoteChanges` likewise vs remote (all of merged if remote is null).
  - `data class SessionDiff(val toInsert: List<BackupSession>, val toDeleteKeys: Set<String>)` and `fun diffSessions(localKeys: Set<String>, target: List<BackupSession>, validTasbihIds: Set<String>): SessionDiff` — inserts target sessions absent locally whose `tasbihId` is valid; deletes local keys absent from target.

Merge rules (spec REVISION): per id for tasbih and routines — only-one-side-changed-vs-base takes that side; both changed → higher `updatedAt`, tie → remote; present in base but missing on one side → deleted there, unless the other side differs from base (edit beats delete); no base → union with the same conflict rule. A tasbih is kept if any merged routine step references it. Favourites (built-in ids) and daily goal merge three-way; no base → favourites union, goal = remote's if non-null else local's. Sessions = union by key, minus sessions whose `tasbihId` was deleted by the merge; sessions for unknown ids are kept in `merged`.

- [ ] **Step 1: Write failing tests** in `SyncMergeTest` named: `firstSyncEmptyRemoteUploadsEverythingAndDeletesNothing`, `remoteOnlyEditIsPulled`, `bothEditedHigherUpdatedAtWins`, `bothEditedTieRemoteWins`, `deletedLocallyIsDeletedRemotely`, `deletedRemotelyIsDeletedLocally`, `editBeatsDelete`, `tasbihKeptWhileSurvivingRoutineUsesIt`, `noBaseNeverDeletes`, `sessionsUnionByKeyNoDuplicatesOnRepeat` (merge result merged again with itself yields same sessions), `sessionsOfDeletedTasbihDropped`, `sessionsForUnknownTasbihKeptInMerged`, `favouritesThreeWay`, `goalLocalChangedWins`, `goalFirstSyncRemoteWins`, `diffSessionsSkipsInvalidTasbihAndDeletesExtras`. Each asserts on `merged` lists and the two counts.
- [ ] **Step 2: Run** `gradlew.bat :app:testDebugUnitTest --tests "com.dhikr.app.core.sync.SyncMergeTest"` — expect compile failure / FAIL.
- [ ] **Step 3: Implement** `SyncMerge.kt` with the signatures above; use one generic private `mergeById` helper for tasbih and routines (keyed by `id`, `updatedAt` selector).
- [ ] **Step 4: Run the same command** — expect all PASS.
- [ ] **Step 5: Commit** `feat(sync): pure three-way merge for sync`

---

### Task 2: Codec, local state access, session dao

**Files:**
- Create: `core/sync/SyncCodec.kt`, `core/sync/RoomSyncLocal.kt`
- Modify: `core/backup/BackupRepository.kt` (extract `suspend fun buildFile(appVersionName: String): BackupFile`, used by `export`), `core/database/dao/SessionDao.kt`, `core/backup/BackupModels.kt` (add `const val SYNC_FORMAT = "dhikr.sync"`, `SYNC_VERSION = 1`)
- Test: `app/src/test/java/com/dhikr/app/core/sync/SyncCodecTest.kt`

**Interfaces:**
- Produces:
  - `object SyncCodec { fun encode(file: BackupFile): String; fun decode(text: String): BackupFile }` — encode sets `format = SYNC_FORMAT`, `version = SYNC_VERSION`; decode throws `SyncException(Kind.CORRUPT)` for non-JSON/wrong format and `SyncException(Kind.NEWER_FORMAT)` when `version > SYNC_VERSION`. (`SyncException` is defined in Task 3's file; create the class there first or declare it in this task's file — declare it here in `SyncCodec.kt`: `class SyncException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) { enum class Kind { NOT_CONNECTED, AUTH_EXPIRED, NETWORK, QUOTA, NEWER_FORMAT, CORRUPT, CONFLICT, BUSY, UNKNOWN } }`.)
  - `interface SyncLocal { suspend fun readState(): BackupFile; suspend fun applyState(target: BackupFile) }`
  - `class RoomSyncLocal(database: AppDatabase, backup: BackupRepository, preferencesRepository: AppPreferencesRepository, appVersionName: String) : SyncLocal`
  - `SessionDao`: `@Query("DELETE FROM session WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)` (existing `getAll()` returns entities with ids).

`applyState(target)` in one `database.withTransaction`: (1) delete local custom routines not in target (`routineDao.deleteRoutine`), (2) delete local custom tasbih not in target (`tasbihDao.deleteByIds`), (3) upsert target tasbih (`upsertAll`) and routines (`upsertRoutine` + `replaceSteps`), (4) set built-in favourites to exactly `target.builtInFavorites` (clear others via `setFavorite`), (5) sessions via `diffSessions` using local `getAll()` mapped to keys (delete by id, insert via `SessionDao.insert`), valid ids = `tasbihDao.getAllIds()`. Then set the daily goal if `target.preferences.dailyGoalTarget != null` using the existing daily-goal setter in `AppPreferencesRepository` (~line 59), coerced to 1..99_999.

- [ ] **Step 1: Write failing tests** in `SyncCodecTest`: `roundTripPreservesData`, `rejectsWrongFormat`, `rejectsNewerVersion` (kind `NEWER_FORMAT`), `rejectsGarbage` (kind `CORRUPT`), `ignoresUnknownKeys`.
- [ ] **Step 2: Run** `gradlew.bat :app:testDebugUnitTest --tests "com.dhikr.app.core.sync.SyncCodecTest"` — FAIL.
- [ ] **Step 3: Implement** the codec (reuse a `Json { ignoreUnknownKeys = true; encodeDefaults = true }`), the `buildFile` extraction (behaviour of `export` unchanged), `deleteByIds`, and `RoomSyncLocal` as specified (`readState()` = `backup.buildFile(appVersionName)`). `RoomSyncLocal` itself has no JVM test (Room); it is covered by Task 3's fake and the build.
- [ ] **Step 4: Run** the codec tests plus existing backup-related tests: `gradlew.bat :app:testDebugUnitTest` — PASS; then `gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(sync): sync codec and Room-backed local state`

---

### Task 3: Sync engine

**Files:**
- Create: `core/sync/SyncEngine.kt`, `core/sync/FileSyncFiles.kt`
- Test: `app/src/test/java/com/dhikr/app/core/sync/SyncEngineTest.kt` (with in-test fakes)

**Interfaces:**
- Consumes: `mergeState`, `SyncCodec`, `SyncLocal`, `SyncException` (Tasks 1–2).
- Produces:
  - `data class RemoteFile(val content: String, val etag: String?)`
  - `interface CloudProvider { suspend fun download(): RemoteFile?; suspend fun upload(content: String, ifMatch: String?): String /* new etag */ }` — `upload` throws `SyncException(Kind.CONFLICT)` when `ifMatch` no longer matches.
  - `interface SyncFiles { fun readBase(): String?; fun writeBase(text: String); fun deleteBase(); fun readSnapshot(): String?; fun writeSnapshot(text: String) }`
  - `class FileSyncFiles(dir: java.io.File) : SyncFiles` (files `base.json`, `snapshot.json`, atomic write via temp+rename).
  - `data class SyncSummary(val localChanges: Int, val remoteChanges: Int)`
  - `class SyncEngine(local: SyncLocal, files: SyncFiles) { suspend fun sync(provider: CloudProvider): SyncSummary; suspend fun undoLast(): Boolean; fun resetBase() }`

`sync` flow: reject with `Kind.BUSY` if already running (`Mutex.tryLock`); download → decode (errors leave everything untouched) → `local.readState()` → base decode (a corrupt base is treated as null) → `mergeState` → write snapshot of the pre-apply local state (`SyncCodec.encode`) → `local.applyState(merged)` → `provider.upload(encode(merged), etag)`; on `CONFLICT` re-download and redo from the merge step **once**, a second conflict throws → on success `files.writeBase(encode(merged))`. If upload fails the base is not written. `undoLast`: decode snapshot → `local.applyState` → `files.deleteBase()`; returns false if no snapshot. `resetBase` = `files.deleteBase()`.

- [ ] **Step 1: Write failing tests** with `FakeProvider` (in-memory file + etag counter, injectable failures) and `FakeLocal`/`FakeFiles`: `firstSyncUploadsLocalAndWritesBase`, `secondSyncPullsRemoteEdit`, `uploadFailureDoesNotWriteBase`, `conflictRetriesOnceThenSucceeds`, `secondConflictThrows`, `corruptRemoteLeavesLocalAndBaseUntouched`, `newerFormatRemoteRejected`, `concurrentSyncRejectedWithBusy` (use a provider whose `download` suspends on a `CompletableDeferred`), `undoRestoresSnapshotAndClearsBase`, `undoWithoutSnapshotReturnsFalse`.
- [ ] **Step 2: Run** `gradlew.bat :app:testDebugUnitTest --tests "com.dhikr.app.core.sync.SyncEngineTest"` — FAIL.
- [ ] **Step 3: Implement** engine and `FileSyncFiles`.
- [ ] **Step 4: Run** same command — PASS.
- [ ] **Step 5: Commit** `feat(sync): sync engine with snapshot, base and undo`

---

### Task 4: State and token stores, backup exclusions

**Files:**
- Create: `core/sync/SyncStateStore.kt`, `core/sync/SyncTokenStore.kt`
- Modify: `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`
- Test: `app/src/test/java/com/dhikr/app/core/sync/SyncStateStoreTest.kt` (only for pure formatting/parsing helpers)

**Interfaces:**
- Produces:
  - `enum class ProviderId { GOOGLE, MICROSOFT }`
  - `class SyncStateStore(context: Context)` over plain `SharedPreferences("sync_state")`: `var provider: ProviderId?`, `var accountLabel: String?`, `var lastSyncedAt: Long` (0 = never), `var lastResult: String?`; `fun clear()`.
  - `class SyncTokenStore(context: Context)` mirroring `SecureKeyStore` (EncryptedSharedPreferences file `sync_secrets`, `openResettingOnCorruption`): `fun save(provider: ProviderId, refreshToken: String, accessToken: String?, expiresAtMillis: Long)`, `fun refreshToken(provider): String?`, `fun accessToken(provider): Pair<String, Long>?`, `fun clear(provider)`.
- Exclusions: add `sharedpref sync_state.xml`, `sharedpref sync_secrets.xml`, `file sync/` to both cloud-backup and device-transfer in `data_extraction_rules.xml` and to `backup_rules.xml` (update the explanatory comments).

- [ ] **Step 1:** Write failing test `SyncStateStoreTest` only if a pure helper exists; otherwise skip the test and say so in the commit (Android storage classes are not JVM-testable here).
- [ ] **Step 2: Implement** both stores and XML exclusions.
- [ ] **Step 3: Run** `gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** `feat(sync): sync state and encrypted token stores`

---

### Task 5: OAuth wiring (AppAuth) and build config

**Files:**
- Modify: `gradle/libs.versions.toml` (`appauth = "0.11.1"`, library `net.openid:appauth`), `app/build.gradle.kts` (dependency; read `google.clientId` / `microsoft.clientId` from `local.properties` into `buildConfigField` strings, blank default; enable `buildFeatures { buildConfig = true }`; `manifestPlaceholders` `appAuthRedirectScheme` = a dummy, `googleRedirectScheme` = `com.googleusercontent.apps.<clientId prefix before .apps.googleusercontent.com>` or `dhikr.unconfigured`, `microsoftRedirectScheme` = `com.badhon495.dhikr.msauth`), `AndroidManifest.xml` (override `net.openid.appauth.RedirectUriReceiverActivity` with `tools:node="replace"` and two `VIEW`/`BROWSABLE` intent filters using the two schemes)
- Create: `core/sync/OAuthClient.kt`
- Test: `app/src/test/java/com/dhikr/app/core/sync/OAuthConfigTest.kt`

**Interfaces:**
- Produces:
  - `data class OAuthProviderConfig(val authEndpoint: String, val tokenEndpoint: String, val clientId: String, val redirectUri: String, val scopes: List<String>)` and `fun oauthConfig(provider: ProviderId): OAuthProviderConfig?` — returns null when the client ID is blank. Google: `https://accounts.google.com/o/oauth2/v2/auth`, `https://oauth2.googleapis.com/token`, redirect `<googleRedirectScheme>:/oauth2redirect`. Microsoft: `https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize`, `.../token`, redirect `com.badhon495.dhikr.msauth://auth`.
  - `class OAuthClient(context: Context, tokens: SyncTokenStore)`: `fun authorizationIntent(provider: ProviderId): android.content.Intent?` (PKCE, `offline` access for Google via `access_type=offline` + `prompt=consent`); `suspend fun completeSignIn(provider: ProviderId, data: android.content.Intent): String /* account label e.g. email, via id/userinfo call or "Google Drive" fallback */` (exchanges the code, saves tokens); `suspend fun freshAccessToken(provider: ProviderId): String` (refreshes if expiring within 60 s; throws `SyncException(AUTH_EXPIRED)` on `invalid_grant`, `NOT_CONNECTED` if no refresh token).

- [ ] **Step 1: Write failing test** `OAuthConfigTest`: `blankClientIdYieldsNullConfig`, `googleConfigUsesReverseClientIdRedirect`, `microsoftConfigUsesConsumersEndpoint` (make `oauthConfig` take the client ids as parameters with a default reading `BuildConfig`, so the test can pass values).
- [ ] **Step 2: Run** the test — FAIL.
- [ ] **Step 3: Implement** config, `OAuthClient`, gradle and manifest changes.
- [ ] **Step 4: Run** `gradlew.bat :app:testDebugUnitTest` then `gradlew.bat :app:assembleDebug` — both succeed with **no** `local.properties` client IDs present.
- [ ] **Step 5: Commit** `feat(sync): AppAuth OAuth client and build config`

---

### Task 6: Google Drive and OneDrive providers

**Files:**
- Create: `core/sync/GoogleDriveProvider.kt`, `core/sync/OneDriveProvider.kt`, `core/sync/HttpJson.kt` (small `HttpURLConnection` helper mapping status codes to `SyncException`: 401→AUTH_EXPIRED, 403/429 quota→QUOTA, 412→CONFLICT, `IOException`→NETWORK, other→UNKNOWN)
- Test: `app/src/test/java/com/dhikr/app/core/sync/HttpJsonTest.kt` (status-code mapping as a pure function)

**Interfaces:**
- Consumes: `CloudProvider`, `RemoteFile`, `OAuthClient.freshAccessToken`.
- Produces: `class GoogleDriveProvider(oauth: OAuthClient) : CloudProvider`, `class OneDriveProvider(oauth: OAuthClient) : CloudProvider`, `fun mapHttpStatus(code: Int, body: String?): SyncException?` (null for 2xx).

Behaviour: **Google** — find `dhikr-sync.json` via `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='dhikr-sync.json'&fields=files(id,md5Checksum)`; download `GET .../files/{id}?alt=media`; create with multipart upload (`parents:["appDataFolder"]`), update with `PATCH https://www.googleapis.com/upload/drive/v3/files/{id}?uploadType=media`; Drive has no `If-Match` on media upload, so before updating re-read `md5Checksum` and throw `CONFLICT` if it differs from the `ifMatch` value (etag = md5Checksum). **OneDrive** — `GET https://graph.microsoft.com/v1.0/me/drive/special/approot:/dhikr-sync.json:/content` (404 → null; etag from a metadata call or `ETag` header), `PUT` same path with `If-Match: <etag>` header (412 → CONFLICT; omit header when `ifMatch` is null).

- [ ] **Step 1: Write failing test** `HttpJsonTest`: `mapsStatusCodes` (200→null, 401→AUTH_EXPIRED, 412→CONFLICT, 429→QUOTA, 500→UNKNOWN).
- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement** helper and both providers (all calls on `Dispatchers.IO`).
- [ ] **Step 4: Run** `gradlew.bat :app:testDebugUnitTest` and `gradlew.bat :app:assembleDebug` — PASS. Note in the commit: real provider calls are untested until the client IDs exist (manual device test).
- [ ] **Step 5: Commit** `feat(sync): Google Drive and OneDrive providers`

---

### Task 7: ViewModel, Settings UI, strings, wiring

**Files:**
- Create: `feature/settings/SyncViewModel.kt`
- Modify: `feature/settings/SettingsScreen.kt` (new `SettingsSection` "Cloud sync" directly after the Backup section at ~line 312), `DhikrApp.kt` (construct sync objects inside the `ROUTE_SETTINGS` destination, like `backupRepository`, ~line 476), `res/values/strings.xml`, `res/values-bn/strings.xml`
- Test: `app/src/test/java/com/dhikr/app/feature/settings/SyncViewModelTest.kt`

**Interfaces:**
- Consumes: `SyncEngine`, `OAuthClient`, `SyncStateStore`, `SyncTokenStore`, providers.
- Produces `class SyncViewModel(engine: SyncEngine, state: SyncStateStore, oauth: OAuthClient, tokens: SyncTokenStore, providerFactory: (ProviderId) -> CloudProvider, configured: (ProviderId) -> Boolean, clock: () -> Long)` with:
  - `val ui: StateFlow<SyncUi>`; `data class SyncUi(val provider: ProviderId?, val accountLabel: String?, val lastSyncedAt: Long, val running: Boolean, val message: SyncMessage?, val canUndo: Boolean, val googleConfigured: Boolean, val microsoftConfigured: Boolean)`; `sealed interface SyncMessage { Success(localChanges, remoteChanges); Error(kind: SyncException.Kind); Undone; NoUndo }`
  - `fun connect(provider: ProviderId): Intent?` (null if not configured), `fun onSignInResult(provider: ProviderId, data: Intent?)`, `fun disconnect()` (clears tokens, state, and `engine.resetBase()`), `fun syncNow()`, `fun undo()`. Connecting a different provider than the stored one calls `engine.resetBase()` first. Ignore `syncNow` while `running`.
- UI: provider rows (Google Drive / OneDrive) showing "Not set up yet" and disabled when unconfigured, Connect/Disconnect, account label, **Sync now** button (reuse `BackupActionRow` look), last-synced text, message line, "Undo last sync" row with a note that the next sync re-applies cloud changes, and a one-line note that all devices must use the same provider. Error copy per `SyncException.Kind` (no network, sign-in expired → reconnect, quota/rate limit, cloud file from newer app version, cloud file unreadable, busy). Use `rememberLauncherForActivityResult(StartActivityForResult)` for the AppAuth intent.

- [ ] **Step 1: Write failing tests** `SyncViewModelTest` (fakes for engine deps): `syncNowIgnoredWhileRunning`, `syncNowSuccessSetsMessageAndLastSynced`, `errorKindIsSurfaced`, `switchingProviderResetsBase`, `disconnectClearsTokensStateAndBase`, `unconfiguredProviderCannotConnect`, `undoWithoutSnapshotShowsNoUndo`.
- [ ] **Step 2: Run** `gradlew.bat :app:testDebugUnitTest --tests "com.dhikr.app.feature.settings.SyncViewModelTest"` — FAIL.
- [ ] **Step 3: Implement** ViewModel (with `Factory`), UI section, strings (EN + BN), wiring.
- [ ] **Step 4: Run** `gradlew.bat :app:testDebugUnitTest` then `gradlew.bat :app:assembleDebug` — PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(sync): Cloud sync section in Settings`

---

### Task 8: Docs and setup guide

**Files:**
- Create: `docs/cloud-sync-setup.md` (step-by-step Google Cloud + Microsoft Entra registration: Google — new project, enable Drive API, OAuth consent screen (External, publish to production, scope `drive.appdata`), create **Android** OAuth client with package `com.badhon495.dhikr` + SHA-1 of debug and release keys, put the client ID in `local.properties` as `google.clientId`; Microsoft — Entra app registration, "Personal Microsoft accounts only" (or any + personal), platform "Mobile and desktop applications" with redirect `com.badhon495.dhikr.msauth://auth`, API permissions `Files.ReadWrite.AppFolder` and `offline_access`, client ID into `microsoft.clientId`; how to get the SHA-1 with `gradlew.bat signingReport`)
- Modify: `PRIVACY_POLICY.md` (data goes only to the user's chosen provider, only on Sync now, app-folder scope, tokens encrypted on device, disconnect removes them), `README.md` (feature bullet + link), `CHANGELOG.md` (new `[Unreleased]` → Added: Cloud sync)

- [ ] **Step 1:** Write the three doc edits and the setup guide.
- [ ] **Step 2: Run** `gradlew.bat :app:testDebugUnitTest` and `gradlew.bat :app:assembleDebug` — all PASS (final whole-branch check).
- [ ] **Step 3: Commit** `docs: cloud sync setup guide, privacy policy, changelog`
