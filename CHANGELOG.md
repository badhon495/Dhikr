# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [1.4.0] - 2026-10-28

versionCode 9

### Added
- **Prayer-linked reminders** — a dhikr reminder a set time after any of the
  five prayers. Times are calculated on the device from a chosen city or typed
  coordinates (no location permission); five calculation methods, Standard or
  Hanafi Asr. Reminders re-arm after reboot, app update and time-zone change.
- **Hijri date** on Home, with a ±2 day adjustment for local moon sighting.
- **Seasonal banner** on Home for Ramadan and its last ten nights, Arafah,
  the days of Dhul Hijjah, Ashura, Friday (salawat and Surah al-Kahf), the
  white days, and the eve of a Sunnah fast — with a shortcut to the fitting
  dhikr where there is one.
- **Sunnah fasting reminder** (opt-in): at 8 pm when tomorrow is Monday,
  Thursday, a white day, Arafah or Ashura. Never on Eid or the days of
  Tashreeq.
- **Qibla compass**: direction from the saved prayer location, with a live
  needle on phones with a compass sensor. Sensors run only while it's open.
- **Per-prayer routines**: choose which routine each prayer reminder opens.
- **Consistency card** on Insights: current run, longest run and gentle
  milestones (7 / 15 / 23 / 30 / 100 days).
- **Weekly goal** with a progress bar on Insights.
- **Share today's dhikr** as plain text from Insights.
- **Export history as CSV** from Settings → Backup.
- **Count with volume buttons** and **keep screen awake** options for the
  counter.
- **Quick Settings tile** that counts the active tasbih.
- **Launcher shortcuts** for Count, Insights and favourite routines.
- Counter text size setting.
- **Dua collection** — 20 seeded daily-life duas with add / edit / delete,
  category filter dropdown and search. Has its own section on Home.
- **Google Drive backup** (optional) — sign-in, encrypted token storage,
  Drive v3 sync, and sync controls in Settings. Documented in the privacy policy.
- **Current prayer waqt** shown on Home.
- **Favorites** on Home, with an empty-state hint.
- **Bangla fields** in the routine and tasbih editors.
- **App-wide Snackbar** feedback with localized messages for actions.
- Hint under the prayer offset setting.
- **Routine step navigation** — Prev/Next now work inside a routine: Prev
  previews earlier completed steps (read-only), Next walks back toward the live
  step. Future steps stay locked.
- Counter shows elapsed time next to routine progress (e.g. `3/7  ·  02:15`).
- GNU GPL v3 license.

### Changed
- Backups now include the new settings and the auto-theme times.
- The widget's [+] counts into the open counter session when there is one.
- Settings and Insights use accordion-style collapsible sections.
- Insights shows summaries for the last 3 months.
- Home layout simplified; routines and favorites display logic optimized.
- Smoother animations across screens (new `animatedFill`, tuned transitions).
- Backup models carry reminder and benefit fields.
- Dua entry point removed from Settings (lives on Home).

### Fixed
- Laps counted past a dhikr's goal were lost when the app was closed by the
  system, and the next taps weren't recorded in history.
- Possible crash on Android 7.x whenever the counter vibrated.
- Restoring a backup dropped the Auto (time-based) theme.
- Home, widget and tile showed "0/1" for one-tap dhikr instead of laps done.
- Home goal ring squeezed into an oval on narrow phones.
- About disclaimer was missing in Bangla.
- Tab-switch slide direction on Home.
- Dua citation for after-eating (Abu Dawud 4023 → 3850).
- Navigation crash on cold start before onboarding finished.
- Stray navigation after Activity recreation (one-shot Intent extras now cleared).
- Jarring cut on language change.
- Drive authorization: Play Services Task cancellation now told apart from
  coroutine cancellation; Drive network error mapped to a status string.
- Dua edit kept order / category order unless the category changed.

## [1.3.0] - 2026-10-19

versionCode 8

### Added
- **Screen dimming** — app-wide adjustable dimming with a brightness slider,
  toggled from the Counter screen.
- Strong-borders option for the dynamic color scheme.
- New dhikr entries; routine step additions now sync to existing installs.

### Changed
- Counter shows routine progress (`done/total`) instead of pace during routines.

### Fixed
- Screen dim toggle silently doing nothing in release builds.
- Duplicate/stale built-in dhikr left over from older seed versions are
  removed on startup (favourites and routine-referenced rows are kept).
- Tasbih Library keeps its scroll position when returning via back.

## [1.2.3] - 2026-10-15

versionCode 7

### Added
- **Auto theme** mode — switches between light and dark at custom start
  times set in Settings.

### Changed
- Tasbih Library sorting options and more responsive layout.
- Home and Insights screens polished for better responsiveness.
- Updated seed data for dhikr routines (database version bump).
- Backup / data-extraction rules updated for the new theme preferences.

## [1.2.1] - 2026-09-17

versionCode 5

### Added
- Sorting options in the Tasbih Library.
- Skip-confirmation dialog in onboarding.
- Multi-resolution status-bar notification icon assets.
- New string resource for the "view past step" hint on the counter screen.

### Changed
- Haptic feedback settings: benefits language now syncs with app language.
- Dhikr placeholders and session label formatting on the counter screen.
- Routine steps for morning/evening presets; database version bump.
- Database seeding logic; clearer settings screen text.
- Counter step target counts now reflect correctly; better past-step display.
- Tasbih counter state refactor for more reliable undo and session restore.

## [1.2.0] - 2026-09-07

versionCode 4

### Added
- Bilingual **English / Bangla** dhikr content — UI and all bundled dhikr,
  routines and situational duas are now translated, switchable from Settings.
- New guided adhkar collections with routine steps: **Morning adhkar**,
  **Evening adhkar**, **Istighfar**.
- **Notes** on any tasbih — add a personal note from the counter or the
  tasbih library, view it in a dialog.
- **Previous / next** navigation between tasbihs on the counter screen.
- **Collapsible steps** in routine cards (`+N more` / `Show less`).
- **Stronger borders** accessibility setting — sharpens card edges,
  dividers and progress rings.
- Onboarding step to pick **language and theme** on first launch.
- Insights: **daily goal met** indicator; default daily goal raised to 500.
- Tooltips and content descriptions on icon buttons for screen-reader and
  accessibility support.
- Settings disclaimer and bundled Privacy Policy.

### Fixed
- Home and Insights content no longer stays stale after switching language.

### Changed
- More resilient secure-key storage (handles corrupted preferences).
- Counter session tracking now follows foreground/background state.
- Release APK build tuned: ABI splits (arm64-v8a, armeabi-v7a) plus a
  universal APK; `.aab` builds stay single-artifact.

## [1.1] - 2026-09-04

- Privacy Policy document.
- Resilient preferences handling for secure key storage.
- Earlier: counter, routines, tasbih library, insights, reminders,
  home-screen widgets, routine sharing, performance/baseline-profile work.
