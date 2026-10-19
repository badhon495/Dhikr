# Cloud Sync (Google Drive / OneDrive) — Design

Date: 2026-10-19
Status: Draft, awaiting approval

## Intent

Let one person keep their Dhikr data identical across their own devices
(second phone, new phone) by syncing through their own Google Drive or
OneDrive account, in the spirit of the Obsidian plugin Remotely Save: no
server run by the app author, no account with the app, the user owns the
storage. Today the only option is manual JSON backup/restore to a local file
(`core/backup`, spec `2026-09-01-manual-backup-design.md`, which called this
"Phase 2").

## Goals

- Sync these between devices: custom tasbih, custom routines (+ steps),
  favourite flags on built-in tasbih, session history (what Insights shows),
  and the daily-goal setting. (Scope = author's guess extending the user's
  "insights and custom tasbih"; user to confirm.)
- Providers: Google Drive and OneDrive only. One active provider at a time.
- Sync runs only when the user taps **Sync now** in Settings. (User decision:
  no automatic/background sync, no WorkManager.)
- Two-way: changes made on either device end up on both, including deletions
  of custom tasbih/routines.
- Works offline-first: the app is unchanged without sync; sync failing never
  affects counting.

## Non-goals

- Other providers (Dropbox, S3, WebDAV, iCloud…).
- Background, scheduled or push-triggered sync.
- Real-time or multi-device simultaneous editing.
- Syncing per-device settings (theme, haptics, motion, dim, auto-counter), the
  Gemini API key, or the in-progress counter session / daily progress rows.
- Syncing built-in tasbih content or preset routines (reseeded by the app).
- End-to-end encryption of the cloud file (data is not sensitive credentials;
  revisit later).

## Anti-goals (would be failure even if it "works")

- Duplicating or double-counting history after repeated syncs.
- A sync silently erasing data on one device with no way back.
- Any telemetry, any third-party server, location or extra permissions beyond
  INTERNET.
- Slowing startup or the counter tap path (no sync work at launch).

## Constraints

- Android, Kotlin, Compose, Room, DataStore, minSdk 24; `applicationId`
  `com.badhon495.dhikr`. Lightweight dependency policy (`plan.md` §2): prefer
  AppAuth-Android (OAuth 2.0 + PKCE in a Custom Tab) plus the existing
  `HttpURLConnection` + kotlinx-serialization style (as `GeminiClient`) over
  Google Play Services or MSAL. Builder's call to deviate with justification.
- Cloud storage is the provider's **app-private folder**, so the app cannot
  see the user's other files: Drive scope `drive.appdata` (`appDataFolder`);
  OneDrive scope `Files.ReadWrite.AppFolder` + `offline_access` (App Folder).
- Tokens stored encrypted on device (reuse the `SecureKeyStore` /
  `ResilientPrefs` pattern from `core/ai`). Disconnect deletes them.
- DB is destructive-migration (`fallbackToDestructiveMigration`); a schema
  change that adds sync metadata will wipe local data unless a real migration
  is written. **Must write a proper Room migration for this change.**
- Privacy policy (`PRIVACY_POLICY.md`) must be updated: data leaves the device
  only to the user's chosen provider, only when they tap Sync now.
- Prerequisite the user performs once: register an OAuth app with Google
  (Cloud project, Android/installed-app client, `drive.appdata` scope) and
  with Microsoft (Entra app registration, public client, redirect URI for the
  app). The builder provides step-by-step instructions and reads client IDs
  from `local.properties`/BuildConfig, never hard-coded secrets (public
  clients have none).

## Why the current backup code is not enough

`BackupRepository.restore` only upserts and never deletes, and inserts
sessions with a freshly generated `id`, so re-running it duplicates history.
Sync needs: stable identity for every synced row, an "updated at" per row, and
deletion records ("tombstones").

## Design

### Data model changes (author's proposal; builder may refine)

- `SessionEntity`: add a stable `uuid` (unique) so a session is identified
  across devices; existing rows get one on migration. Sessions are
  append-only, so merge = union by `uuid`.
- `TasbihEntity` (custom only) and `RoutineEntity` already have `id` and
  `updatedAt`; merge = last-writer-wins by `updatedAt` (ties: remote wins).
  Routine steps travel with their routine.
- New `tombstone` table: `(kind, id, deletedAt)` written when a custom
  tasbih/routine is deleted. A tombstone newer than the other side's
  `updatedAt` deletes it; edits newer than the tombstone resurrect it.
  Tombstones are pruned after 90 days.
- Built-in favourites: synced as set of ids with per-id timestamp (builder's
  call; simple LWW per flag is acceptable).

### Remote layout

A single file `dhikr-sync.json` in the provider's app folder, same JSON
conventions as `BackupFile` (format/version fields; reject newer versions).
Sessions may be chunked per month if size becomes a concern (builder's call);
start with one file. Use the provider's ETag / revision to avoid clobbering a
concurrent upload (re-download and re-merge on conflict, retry once).

### Sync algorithm (Sync now)

1. Download remote file (absent = empty).
2. Merge remote + local per rules above into a result set; apply result to
   Room in one transaction.
3. Upload merged file with the ETag precondition.
4. Record `lastSyncedAt` and per-provider account label.

A summary is shown ("Pulled 3 sessions, pushed 12, 1 deleted") or a plain
error. First sync on a device with existing data merges, never overwrites;
a local safety snapshot (the existing manual-backup JSON, kept in app cache,
last one only) is written before applying remote changes so a bad merge can
be undone from Settings.

### UI

Settings → new "Cloud sync" section: provider picker (Google Drive /
OneDrive), Connect / Disconnect (shows account), **Sync now** button with
spinner, last-synced time, last result/error line, "Undo last sync". Uses
existing Settings components and English + Bangla strings
(`values`, `values-bn`). No new screen needed.

### Errors to handle (user-readable)

No network; token expired/revoked → reconnect prompt; provider quota/rate
limit; remote file from a newer app version; file corrupt; user cancels
sign-in; sync tapped twice (ignore while running).

### Testing

Pure-Kotlin merge function with JVM unit tests (the project already has this
pattern): duplicate sessions, edit vs edit, edit vs delete both ways,
tombstone expiry, first-sync union, newer-format rejection. HTTP/provider
layer behind an interface with a fake for tests. Manual device test of real
OAuth on both providers.

## REVISION (supersedes "Data model changes", tombstones and migration above)

After reading the DB code (v29, `exportSchema = false`, destructive
migration only, never a hand-written migration) the author chose a design with
**no Room schema change, no tombstones, no migration**:

- **Three-way merge against a local "base" file.** After every successful
  sync the merged state is saved locally as `filesDir/sync/base.json` (same
  `BackupFile` shape). Next sync compares local (L), remote (R) and base (B).
  An item present in B but missing from L or R was deleted on that side.
  Deleting needs no hook in the delete code paths and no timestamps.
- **Session identity** is derived, not stored: key = `tasbihId|startedAt|
  endedAt|count`. Sessions merge as a union by key.
- Tasbih/routine conflicts: if only one side changed vs B, take that side;
  if both changed, higher `updatedAt` wins (tie: remote wins). Delete-vs-edit:
  the edit wins (item is kept/resurrected).
- Favourites on built-in tasbih (id set) and the daily goal merge three-way
  against B too; with no B (first sync, or after Undo/provider switch/
  disconnect), sets union and goal = remote if present, else local, and no
  deletions are propagated.
- A tasbih is never deleted by a merge while any surviving merged routine
  still has a step using it (the DB's RESTRICT foreign key); it is kept.
- Sync metadata (provider, account label, last synced time, last summary)
  lives in a small plain `SharedPreferences` file `sync_state`; OAuth tokens in
  an encrypted store mirroring `SecureKeyStore`. Both, and `sync/`, are
  excluded from Android auto-backup/transfer.
- Client IDs come from `local.properties` (`google.clientId`,
  `microsoft.clientId`). With none set the app still builds and runs; the
  provider rows show "Not set up yet" and Connect is disabled.
- Known limitation: toggling favourite on a custom tasbih/routine does not
  bump `updatedAt`, so in a both-sides-changed conflict it can lose.

## Answers from the codebase and author's calls (after builder check)

Verified in code:
- Deleting a tasbih is a hard delete and `SessionEntity` has `ForeignKey ...
  onDelete = CASCADE` on `tasbihId`, so a tasbih's sessions are deleted with
  it. Sync mirrors this: a tasbih tombstone applied on the other device
  cascades its sessions. No orphan sessions can exist. Sessions are
  therefore append-only except via this cascade, so no session tombstones.
- No "clear history" or session-delete action exists today (no DAO delete for
  sessions). If one is added later it must add a `clearedBefore` watermark or
  session tombstones; note this in code comments.

Author's calls (user may override):
- Tombstones are written only for user-initiated deletes of custom
  tasbih/routines, never by the startup purge/reseed (`retiredBuiltInIds`,
  routine-step sync). Built-in rows are never synced, so they never produce
  tombstones.
- Migration must be a real Room migration, not a destructive reset (see
  Constraints).
- Daily goal and favourite flags get an `updatedAt` (DataStore key
  `goal_updated_at`; per-id timestamp for favourites). A device that never
  changed its goal has no timestamp and loses to any stamped value, so a new
  phone's default goal cannot overwrite the real one.
- "Undo last sync" restores the local pre-sync snapshot only and does not push
  to the cloud; the next Sync now re-applies remote changes, and the UI says
  so. The snapshot lives in app files (not cache) so the OS can't evict it.
- Switching provider leaves the old remote file untouched and resets local
  sync state (`lastSyncedAt`, per-remote tombstone bookkeeping). The Settings
  copy warns that all your devices must use the same provider to converge.
- Build order: sync engine, merge, migration, Settings UI against a fake
  provider first; real OAuth for each provider last, so work isn't blocked on
  the registrations.

## Left to the builder

Dependency choice for auth, exact JSON schema of the remote file, tombstone
and favourite details, retry/ETag mechanics, chunking, migration code,
strings, error copy, README/PRIVACY/CHANGELOG updates.

## Open items needing the user

- Confirm sync scope (list under Goals).
- Confirm they will do the Google + Microsoft app registrations themselves.
- Google consent-screen status: in "Testing" mode refresh tokens expire after
  7 days and only listed test users can sign in. Publishing the consent screen
  ("In production") is free for the non-sensitive `drive.appdata` scope and
  avoids weekly re-login. Author recommends publishing it. Also: is the app
  distributed beyond the author (Play Store / sideload)? This decides the
  release SHA-1 and redirect registration.
