# Dhikr — Features

Native Android Tasbih / Dhikr counter. Offline-first, ad-free, no account required.
Reflects the app as of v1.4.1 plus the high-contrast option added afterwards.

## Counting

- Tap-to-count engine with session tracking and undo.
- Progress ring and count number; both scale with the **Counter text size** setting.
- Goal laps: counts continue past a dhikr's target and laps are recorded.
- Session survives the system closing the app; tracking follows foreground/background state.
- Elapsed time shown beside routine progress (e.g. `3/7 · 02:15`).
- **Previous / next** navigation between tasbihs on the counter screen.
- **Routine step navigation**: Prev previews earlier completed steps (read-only), Next walks back toward the live step; future steps stay locked.
- Choose which script the counter shows (pronunciation, Arabic, etc.).
- **Personal notes** on any tasbih, viewable in a dialog.
- **Haptic feedback** with selectable mode.
- **Count with volume buttons** (opt-in).
- **Keep screen awake** while counting (opt-in).
- **Screen dimming**: app-wide, toggled from the counter, brightness set by a slider.
- **Auto counter** (experimental, off by default): accelerometer wrist-flick detection.
- **Quick Settings tile** that counts the active tasbih.

## Tasbih library

- Built-in dhikr library, bilingual (English / Bangla).
- Create, edit and delete custom tasbih, including Bangla fields.
- Built-in tasbih can be deleted unless a routine uses them.
- Favorites.
- Sorting options and a responsive layout; scroll position is kept when returning.
- Per-tasbih personal notes.
- Optional **AI benefits** (virtues) text per tasbih, using your own Google Gemini API key; the key is stored encrypted on-device and results are cached locally. Prompt can be customised, and the benefits language follows the app language.

## Routines

- Multi-step dhikr sequences with auto-advance.
- Preset routines: Morning adhkar, Evening adhkar, Istighfar and others.
- Create and edit routines, including Bangla fields.
- Collapsible step lists on routine cards (`+N more` / `Show less`).
- Favourite routines shown on Home.
- **Routine sharing** by share code, with an import screen and import planning.
- Reminders per routine (see below).

## Dua collection

- 20 seeded daily-life duas with citations.
- Add, edit and delete your own.
- Category filter and search.
- Own section on Home.

## Home

- Daily goal ring and resume-session card.
- Favorites and routine shortcuts.
- Current prayer waqt.
- **Hijri date** with a ±2 day adjustment for local moon sighting.
- **Seasonal banner**: Ramadan and its last ten nights, Arafah, days of Dhul Hijjah, Ashura, Friday (salawat, Surah al-Kahf), white days, and the eve of a Sunnah fast, with a shortcut to the fitting dhikr.
- **Backup nudge** card when the last backup is stale (14 days; 7-day snooze).

## Insights

- Daily, weekly and all-time totals.
- 7-day chart.
- Consistency card: current run, longest run, gentle milestones (7 / 15 / 23 / 30 / 100 days), with one bridged missed day per 7 days.
- Weekly goal with a progress bar; daily goal met indicator.
- Monthly history for the last 3 months.
- All-dhikr breakdown screen.
- Share today's dhikr as plain text.

## Reminders and prayer

- Scheduled local notifications per routine, on chosen days and time; re-armed after reboot, app update and time-zone change.
- Tapping a reminder opens the routine counter.
- **Prayer-linked reminders**: a dhikr reminder a set time after any of the five prayers.
  - Times are calculated on-device from a chosen city or typed coordinates (no location permission).
  - Five calculation methods; Standard or Hanafi Asr.
  - Choose which routine each prayer opens.
- **Sunnah fasting reminder** (opt-in) at 8 pm before Monday, Thursday, white days, Arafah or Ashura; never on Eid or Tashreeq.
- **Qibla compass**: direction from the saved prayer location, live needle on phones with a compass sensor; sensors run only while it is open.
- Notification-permission notice with an Open settings button when reminders are on but notifications are blocked.
- Notification channel name and description follow the app language.

## Widgets and shortcuts

- **Counter widget**: a `[+]` button counts without opening the app (into the open counter session when there is one).
- **Insights widget**: today vs goal, week, all-time.
- **Launcher shortcuts** for Count, Insights and favourite routines.

## Backup and sync

- JSON export and import of tasbih, routines, favorites, history and settings.
- **Export history as CSV**.
- Optional **Google Drive backup**: sign-in, encrypted token storage, Drive v3 sync and sync controls.
- Routine sharing by code (see Routines).
- Last-backup line in Settings.

## Appearance and accessibility

- Themes: System, Light, Dark, and **Auto** (switches at custom times).
- **Material You** dynamic colors on Android 12+.
- **High contrast** palette: pure black/white with darker accents (overrides Material You).
- **Stronger borders** for cards, dividers and ring tracks.
- Reduced motion option; colors crossfade on theme change.
- Counter text size setting.
- Tooltips and content descriptions on icon buttons for screen readers.
- App-wide Snackbar feedback with localized messages.

## Language and onboarding

- English and Bangla UI, plus bilingual dhikr, routines and duas; switchable in Settings.
- Onboarding on first launch, including language and theme choice and a skip confirmation.

## Privacy and trust

- No account, no ads; works offline.
- Network is used only for the optional Gemini benefits and Google Drive backup.
- Bundled Privacy Policy and an About disclaimer.
- Licensed GNU GPL v3.

## Engineering

- Kotlin, Jetpack Compose, Material 3, Room, DataStore; minSdk 24.
- Baseline profile and Macrobenchmark modules.
- ABI-split release APKs plus a universal APK; AAB builds.
- Unit tests, including an English/Bangla string-parity test.
