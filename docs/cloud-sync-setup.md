# Cloud sync — one-time setup

Cloud sync needs an OAuth "client ID" from Google and/or Microsoft. A client ID is a
public identifier, not a secret, and registering is free. Until you add the IDs the app
builds and runs normally, and the Cloud sync rows show "Not set up yet".

Put the IDs in `local.properties` at the repo root (git-ignored):

```
google.clientId=1234567890-abcdefg.apps.googleusercontent.com
microsoft.clientId=00000000-0000-0000-0000-000000000000
```

Rebuild after editing. You can set up one provider or both.

## Google Drive

1. Go to <https://console.cloud.google.com/> and create a project (any name).
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **APIs & Services → OAuth consent screen** (Google Auth Platform → Branding/Audience):
   - User type **External**. Fill app name, support email, developer email.
   - Add scope `https://www.googleapis.com/auth/drive.appdata` (non-sensitive; no
     Google verification needed). `openid` and `email` are also used.
   - **Publish the app** (Audience → *Publish app* / "In production"). In *Testing* mode
     refresh tokens expire after 7 days and only listed test users can sign in.
4. **Credentials → Create credentials → OAuth client ID → Android**:
   - Package name `com.badhon495.dhikr`.
   - SHA-1: run `gradlew.bat signingReport` and use the SHA1 of the key you sign with
     (debug key for development, your release keystore for the release build). Create one
     client per key, or add both if the console allows.
   - Under **Advanced settings**, turn on **Enable custom URI scheme** (the app signs in with
     a `com.googleusercontent.apps.<id>:/oauth2redirect` redirect through the browser).
5. Copy the client ID into `local.properties` as `google.clientId`.

## Microsoft OneDrive

1. Go to <https://entra.microsoft.com/> → **Applications → App registrations → New
   registration**. (A free personal Microsoft account works; Microsoft may ask you to create
   a free Azure/Entra tenant first.)
2. Name it; **Supported account types**: *Personal Microsoft accounts only* (or any
   directory + personal).
3. After creating: **Authentication → Add a platform → Mobile and desktop applications**,
   add the custom redirect URI `com.badhon495.dhikr.msauth://auth`. Enable
   **Allow public client flows**.
4. **API permissions → Add → Microsoft Graph → Delegated**: `Files.ReadWrite.AppFolder`,
   `offline_access`, `openid`, `email`, `profile`. No admin consent needed.
5. Copy the **Application (client) ID** into `local.properties` as `microsoft.clientId`.

## Testing a sync

Install the build on two devices (or one device, then clear data and reinstall), connect the
same provider on both, tap **Sync now** on each. The sync file lives in the provider's app
folder as `dhikr-sync.json` (Google: hidden `appDataFolder`; OneDrive: `Apps/<app name>/`).

## Known risks to check on first real run

- Google may reject the custom-scheme redirect for Android clients unless
  *Enable custom URI scheme* is on (step 4). If sign-in fails with `redirect_uri_mismatch`,
  check that and the SHA-1.
- OneDrive: the app-folder name is chosen by Microsoft from the registration name.
