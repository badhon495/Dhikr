# Privacy Policy for Dhikr

**Last updated:** 23 October 2026

Dhikr ("the app") is a native Android Tasbih / Dhikr counter developed by the
Dhikr developer ("we", "us"). This policy explains what data the app handles and
how it is treated. It applies to the app distributed as `com.badhon495.dhikr` on
Google Play.

## Summary

- The app has **no account system** and **no login**.
- We do **not** collect, transmit, or sell any personal data.
- We do **not** use analytics, advertising, tracking, or crash-reporting SDKs.
- All of your data — counts, sessions, custom dhikr, routines, history,
  reminders, goals, and settings — is stored **only on your device**.
- The single optional feature that contacts the internet is the AI benefits
  feature, and only when you enable it with your own API key (see below).

## Data the app stores on your device

The app creates and stores the following locally, in its private app storage
(a Room database, Jetpack DataStore preferences, and Android encrypted
preferences). None of it leaves your device except as described in
"Data you choose to export" and "Optional AI benefits feature" below.

| Data | Purpose |
|------|---------|
| Dhikr / Tasbih counts, sessions, and undo state | Core counting feature |
| Custom Tasbih you create (name, target, text) | Your personal dhikr library |
| Routines (multi-step dhikr sequences) | Routine feature |
| Daily goals, favorites, resume state | Home screen |
| Daily / weekly / all-time totals, history log, consistency calendar | Insights feature |
| Reminder schedules | Local scheduled notifications |
| Widget state (current count) | Home screen widgets |
| Theme, language, onboarding, and other app settings | App preferences |
| Your Google Gemini API key (if you enter one) | Optional AI benefits feature; stored with Android `EncryptedSharedPreferences` |
| Cached AI-generated "virtues / benefits" text per Tasbih | Avoids repeat network requests |

You can erase all of this at any time by clearing the app's storage in Android
Settings or uninstalling the app.

## Data you choose to export

The app has a Backup feature (Settings → Export) that writes your app data to a
JSON file, and a routine-sharing feature that produces a share code or a
`.dhikrroutine` file. These files are created only when you ask for them, are
saved or shared to the destination **you** pick, and are then outside the app's
control. We never receive them.

## Optional AI benefits feature

The app can show AI-generated notes about the virtues of a given dhikr. This
feature is **off until you turn it on**, and it requires you to supply your own
Google Gemini API key.

When enabled and used:

- The app sends the dhikr text (Arabic / transliteration / translation) and your
  API key directly from your device to Google's Generative Language API
  (`https://generativelanguage.googleapis.com`).
- No other data — no counts, history, device identifiers, or personal
  information — is included in that request.
- The request goes to Google, not to us. Google's handling of it is governed by
  Google's Privacy Policy and the Gemini API terms:
  <https://ai.google.dev/gemini-api/terms> and
  <https://policies.google.com/privacy>.
- The generated text is cached on your device so the request is not repeated.
- Your API key is stored encrypted on your device and is transmitted only to
  Google, only as part of these requests.

Removing the API key in Settings disables the feature. Clearing the cached
benefits text removes it from your device.

## Optional Google Drive backup

The app can back up your data to your own Google Drive. This feature is **off
until you turn it on** by signing in with your Google account in Settings →
Backup.

When enabled and used:

- The app requests only the `drive.file` scope, which means it can see and edit
  only files it creates itself, never the rest of your Drive.
- When you tap "Backup to Drive," the same backup content described in "Data you
  choose to export" is sent directly from your device to a single file
  (`dhikr_backup.json`) in your own Google Drive — not to us.
- "Restore from Drive" downloads and applies that same backup file.
- Your Google sign-in uses Google's Credential Manager and Consent flow. Google's
  handling of your data is governed by
  <https://policies.google.com/privacy>.
- Signing out removes the app's local sign-in state but does not delete the
  backup file from your Drive.

Disabling the feature or signing out prevents future backups and restores;
existing backups remain on your Drive until you delete them.

## Permissions the app requests

| Permission | Why |
|------------|-----|
| `INTERNET` | For the optional AI benefits feature and the optional Google Drive backup feature, both described above. With both features off, the app makes no network connections. |
| `VIBRATE` | Haptic feedback on counting. |
| `POST_NOTIFICATIONS` | Showing the local reminder notifications you schedule. |
| `RECEIVE_BOOT_COMPLETED` | Re-registering your reminders after the device restarts. |
| `android.hardware.sensor.accelerometer` (optional, `required="false"`) | Only for the experimental, off-by-default auto-counter (wrist-flick) setting. Sensor data is processed in memory to detect a flick and is never stored or transmitted. |

## Children's privacy

The app does not collect personal data from anyone, including children. It
contains no ads and no in-app purchases.

## Data security

Data stays in the app's private storage, which other apps cannot read on a
non-rooted device. The Gemini API key is additionally encrypted at rest using
`androidx.security-crypto`. Network requests to Google use HTTPS.

## Changes to this policy

If this policy changes, the "Last updated" date above will change and the new
version will be published at the same URL used in the Play Store listing.

## Contact

Questions about this policy: badhon495@gmail.com
