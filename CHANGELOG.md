# Changelog

All notable changes to this project are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

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
