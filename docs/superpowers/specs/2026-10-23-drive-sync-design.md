# Google Drive backup/restore — design

## Intent

Settings currently has local backup/restore (Storage Access Framework: user
picks a file location by hand, on-device, each time). Add a second, parallel
option: **"Backup to Drive" / "Restore from Drive"**, using the user's own
Google account and their own Drive storage. Same backup content and format as
the existing local export (`BackupRepository.export`/`restore` — no changes
to that class). This is for users who want their data to survive a device
loss or move to a new phone without manually handling a file.

## Goals

- One-tap sign-in with a Google account, then one-tap backup/restore to/from
  that account's Drive.
- Reuse the existing `BackupFile` JSON format exactly — Drive is just a new
  transport for the same bytes `BackupRepository.export()` already produces.
- Single file on Drive, overwritten on each backup (no version picker, no
  history UI). Drive's own built-in file revision history is the fallback if
  a user needs an older version — out of scope for this app's UI.
- Sign-out must be available and must not delete the Drive file or local
  data — it only drops the local credential.

## Non-goals

- No two-way / automatic / background sync. Backup and restore are both
  explicit user actions, same as the existing local export/import.
- No conflict resolution beyond what `BackupRepository.restore`'s upsert
  semantics already provide (restore merges into local data; it does not
  diff against the last backup).
- No change to the CSV history export — that stays local-file-only.
- Not building a generic "cloud provider" abstraction. This is Google Drive
  specifically; no iCloud/Dropbox/etc.

## Anti-goals (would make this a failure even if it technically works)

- Silently sending any data to Google without the user explicitly signing in
  and explicitly tapping "Backup to Drive" first.
- Requesting a Drive scope wider than `drive.file` (app must only ever see
  files it created, never the user's whole Drive).
- Breaking the "no account" promise: the app itself must not require this
  sign-in for anything else; the rest of the app must work fully offline
  with no account, exactly as before. This is an additional opt-in feature
  only.
- Leaving the Privacy Policy stale — it currently states "no account
  system," "we do not collect/transmit personal data" with one named
  exception (AI feature). This document's feature adds a second named
  exception and the policy file must be updated in the same work.

## Constraints

- minSdk 24, compileSdk 37, Kotlin/Compose, no existing Google Play Services
  or Drive dependencies in `app/build.gradle.kts` today.
- App has no backend server. Auth and storage both go directly from the
  device to Google's APIs — consistent with how the existing AI feature
  (Gemini) talks to Google directly.
- Must keep working fully offline for everyone who never touches this
  feature: the new dependencies must not add startup cost, background
  services, or required permissions beyond what's already declared.
  `INTERNET` permission already exists (for the AI feature) and is reused.
- Build size matters (plan.md #55) — prefer the smallest dependency set that
  does the job over a full `google-api-client` Java library.

## Decisions made (by the user, in brainstorming)

- **Scope**: backup/restore only, not live two-way sync.
- **Format**: reuse `BackupRepository`'s existing `BackupFile` JSON exactly;
  no new serialization.
- **Auth**: standard Google Sign-In (shows the signed-in account, e.g. email,
  in Settings), using the `drive.file` scope (app-created-files-only, not
  full Drive access).
- **Trigger**: manual buttons only — "Backup to Drive" and "Restore from
  Drive" — no automatic/background backup.
- **Versioning**: a single Drive file, overwritten on every backup.

## Decisions left to the builder

- **Libraries**: use Credential Manager
  (`androidx.credentials` + `com.google.android.libraries.identity.googleid`)
  for sign-in (GoogleSignInClient/`com.google.android.gms.auth` is legacy and
  should not be newly adopted), and Google's REST Drive v3 API called
  directly via a minimal client (either the lightweight
  `google-api-client`/`google-api-services-drive` combo or hand-rolled
  OkHttp/Ktor REST calls with the OAuth access token) rather than pulling in
  the full `play-services-drive` GmsCore client, which is heavier and more
  awkward to test. Pick whichever keeps APK size and testability best; note
  the choice and why in the implementation.
- **Where the Drive file lives**: `drive.file` scope can still use
  app-visible "My Drive" (findable by the user, shows in Drive UI) or the
  hidden `appDataFolder` special folder. Since the user chose a visible
  Sign-In flow (not the anonymized AppData-only option), default to a
  regular visible file (e.g. named `dhikr_backup.json`) in My Drive so the
  user can find/inspect/delete it themselves if they want — this matches
  choosing the more transparent, discoverable option over the hidden one.
  Store the returned Drive file id locally (DataStore) after the first
  backup so later backups update-in-place by id rather than searching by
  name or creating duplicates.
- **New module boundary**: add `core/drivesync/` (new package, parallel to
  `core/backup/`) containing:
  - `DriveAuthManager` (or similar) — Credential Manager sign-in/sign-out,
    exposes current signed-in account (nullable email/display name) as a
    Flow or suspend getter.
  - `DriveBackupRepository` — wraps Drive REST calls (upload/update/download
    of the one file), taking the access token from `DriveAuthManager` and
    the JSON bytes from the existing `BackupRepository.export()`/`restore()`.
  - Keep `BackupRepository` itself untouched — it has no Drive/network
    knowledge, same as today.
- **ViewModel/UI**: add a `DriveSyncViewModel` (new, do not overload
  `BackupViewModel`) with sealed `Status`/sign-in state, and a new
  `DriveSyncControls` composable section in `SettingsScreen.kt`, placed in
  the existing Backup section (`SettingsSection(settings_backup)`) below the
  current local export/import rows, or as its own clearly-labeled
  sub-section — builder's call, but it must visually read as "the Drive
  version of the thing right above it," not a disconnected feature.
- **Sign-in UI**: show a "Sign in with Google" row when signed out; once
  signed in, show the account email/name plus "Backup to Drive," "Restore
  from Drive," and "Sign out" rows. Error/working/success states follow the
  same pattern `BackupViewModel.Status` already established (Idle/Working/
  Done/Error) — reuse that shape for consistency, don't invent a new status
  vocabulary.
- **Error handling**: no network, token expired/revoked, Drive API quota or
  5xx, file not found on restore (first-ever restore before any backup
  exists) all need distinct, user-readable messages — follow the tone
  already used in `BackupViewModel`'s existing error strings ("Couldn't
  write the backup file.", etc.) and add `strings.xml` entries alongside the
  existing `settings_backup_*` keys (new keys, e.g.
  `settings_backup_drive_*`).
- **Token storage**: Credential Manager handles credential persistence
  itself (no manual token caching needed for sign-in state across app
  restarts); if a short-lived OAuth access token for Drive API calls needs
  caching, treat it the same security-sensitivity as the existing Gemini API
  key (`SecureKeyStore`/`EncryptedSharedPreferences` pattern in
  `core/ai/SecureKeyStore.kt`) — look at that file for the established
  pattern before inventing a new one.
- **Google Cloud Console setup**: this feature requires a project in Google
  Cloud Console with the Drive API enabled, an OAuth consent screen (scope:
  `drive.file`), and **two** OAuth client IDs — an Android-type client
  (app's `applicationId` `com.badhon495.dhikr` + signing certificate SHA-1)
  for the Drive-scope authorization step, and a Web-type client (used as
  the `serverClientId` passed to Credential Manager's sign-in option) which
  is what actually makes Credential Manager's Google Sign-In produce an ID
  token at all — the Android-type client alone does not do this. Both are
  an external, one-time account-setup step outside the codebase; flag as a
  prerequisite the project owner completes in Cloud Console first. Builder
  leaves placeholders for both client IDs (e.g. `local.properties`/
  `BuildConfig`, not hardcoded or committed) and documents the setup steps
  in the PR description.
- **Sign-in vs. Drive authorization are two separate steps, not one.**
  Credential Manager's `GetSignInWithGoogleOption` + `GoogleIdTokenCredential`
  authenticates *who the user is* (an ID token) — it does not, by itself,
  grant an OAuth access token scoped to `drive.file`. A second step is
  required to mint that access token. Use Credential Manager for the
  sign-in UI (step 1), then use its companion authorization API —
  `androidx.credentials` `ClearCredentialStateRequest`'s sibling, the
  Credential Manager **Authorization** API (`androidx.credentials.registry`
  / Play Services `identity` `AuthorizationClient`, whichever is current
  and stable at the time of building) — to request the `drive.file` scope
  and obtain the access token for the Drive REST calls (step 2). Do not
  fall back to the legacy `GoogleSignInClient`/`GoogleAuthUtil` for this —
  if the Authorization API is impractical at implementation time, stop and
  raise it rather than silently reintroducing the legacy client this doc
  explicitly rules out.
- **Token refresh**: on a Drive REST call failing with an expired/revoked
  token, attempt one silent re-authorization via the Authorization API
  (step 2 above) before surfacing an error; only show the user a "signed
  out, please sign in again" state if that silent attempt also fails. Don't
  force a full manual re-sign-in on every ordinary token expiry.
- **Testing**: unit-test `DriveBackupRepository`/`DriveAuthManager` against
  fakes/mocks of the Drive REST calls (no real network in tests), following
  the existing test patterns for `BackupRepository`. UI/instrumented test
  for the sign-in flow is not required (Credential Manager's system UI isn't
  practically testable in this app's existing test setup) — manual
  verification is acceptable for the sign-in dialog itself.

## What's left to the builder (summary)

Everything under "Decisions left to the builder" above, plus: exact Gradle
dependency versions, exact Drive REST endpoints/error codes to special-case,
exact composable layout/spacing to match `SettingsScreen.kt`'s existing
style, and the Privacy Policy wording update (must land in the same change —
add a new section describing the Drive sync feature, modeled on the existing
"Optional AI benefits feature" section: off by default, requires explicit
sign-in, data goes directly device-to-Google using the user's own account,
scope is limited to files the app created).
