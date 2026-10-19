# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

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
