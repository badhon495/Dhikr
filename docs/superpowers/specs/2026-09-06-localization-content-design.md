# Localization of dhikr content (English / Bangla) — design

**Date:** 2026-09-06
**Branch:** `phase4-localization-content`
**Phase:** 4 sub-project B (localization), content half.

## Problem

UI chrome is already localized (`values/` + `values-bn/`). The gap is
DB-stored dhikr content in `SeedData.kt`:

- `name` — always Latin ("SubhanAllah", "Ar-Rahman")
- `pronunciation` — always Bangla script ("সুবহানাল্লাহ")
- `translation` — one field holding `"বাংলা — English"`
- `note` — Bangla only
- routine names — English only

When the user picks Bangla, everything (tasbih name, pronunciation,
translation, note, routine names, option lists, numbers) must render in
Bangla. When they pick English, everything renders in English including
a Roman-script pronunciation.

## Decisions (from brainstorming)

- Two languages only: English, Bangla. `AppLanguage.SYSTEM` is dropped.
- English mode pronunciation = same words, Roman script.
- Missing content (Roman pronunciations, English notes, Bangla names) is
  drafted in this change; the religious text is reviewed before merge.
- First run: existing `OnboardingScreen` gains a leading Language + Theme
  step. No full onboarding redesign.
- Custom tasbih: whatever the user typed shows in both modes (fallback).
- DB schema change follows the existing pattern —
  `fallbackToDestructiveMigration(dropAllTables = true)`, reseed. Custom
  tasbih + history are lost on update; Backup is the escape hatch.
- "Options" to localize: routine step pickers (falls out of content
  localization), tasbih editor pickers, and numbers as Bangla numerals.

## 1. Language model

- `AppLanguage` enum → `ENGLISH("en")`, `BANGLA("bn")` only.
- `AppLanguage.current`: empty locale list (fresh install) → derive from
  device (`bn` → BANGLA, else ENGLISH). Never "system".
- `AppLanguage.apply` unchanged (list is always non-empty now).
- `LocalAppLanguage` CompositionLocal provided in `DhikrApp`, value
  `AppLanguage.current`. AppCompat recreates the activity on a locale
  switch, so the value stays fresh without extra plumbing.
- Settings language row: 2 options. `settings_language_system` unused.

## 2. Data schema — DB v17

`TasbihEntity` new nullable columns: `nameBn`, `pronunciationBn`,
`translationBn`, `noteBn`. Existing `name` / `pronunciation` /
`translation` / `note` keep their names; semantics become "the English
side". `arabic`, `source` stay single-valued.

`RoutineEntity`: new nullable `nameBn`.

`AppDatabase` version 16 → 17, comment block updated. No hand migration.

Resolver (extension functions on `TasbihEntity`, `RoutineEntity`):

```kotlin
fun TasbihEntity.displayName(lang: AppLanguage): String =
    if (lang == AppLanguage.BANGLA) nameBn?.takeIf(String::isNotBlank) ?: name
    else name
// displayPronunciation, displayTranslation, displayNote likewise
fun RoutineEntity.displayName(lang): String = ...
```

Blank-Bn fallback to the English field is exactly the custom-tasbih
"show as-is" behaviour — custom rows only ever fill the base fields.

## 3. Seed content

`dhikr(...)` and `name(...)` builders gain Bn params. For each built-in:

- split today's `translation` at `" — "` → `translation` (English) +
  `translationBn` (Bangla)
- today's `pronunciation` → `pronunciationBn`; author Roman
  `pronunciation`
- today's `note` → `noteBn`; author English `note`
- `nameBn`: Asma rows already carry a Bangla name in the seed's `bangla`
  param; core duas get a short Bangla title
- preset routine `nameBn`

## 4. Display layer

`LocalAppLanguage` read at each site that shows dhikr/routine text:

- Counter screen + VM (title, script line, notes dialog)
- Tasbih library (rows, notes, actions title) + search across the
  active-language fields + Arabic
- Routines / RoutineEditor / Home routine cards (step names,
  `routine.displayName`)
- Home (favourites, continue-session label)
- `HistoryRepository` id→name map → `id to displayName(lang)`
- Widget session name
- `BenefitsPrompt` — pass `displayName` / `displayPronunciation`

`TasbihEditor` unchanged (custom tasbih edit the base fields; built-ins
are not user-editable). Routine share/import stays English-canonical so
shared text is portable.

## 5. Bangla numerals

`fun Int.localizedDigits(lang): String` — maps 0-9 → ০-৯ for BANGLA,
plain `toString()` for ENGLISH. Applied to numbers rendered via raw
Kotlin interpolation: counter value, "of target", lap X/Y, insights
totals + chart labels, dates, editor lap-target / daily-goal pickers,
routine step counts. `stringResource` `%d` args already localize under
locale `bn`. Stored values stay `Int`.

## 6. Backup

No change. `BackupTasbih` / `BackupRoutine` only ever hold custom rows,
which have no Bn data.

## 7. First-run step

New composable `WelcomeSetupPage` prepended to `OnboardingScreen`'s
pager (or shown before it): Language (English / বাংলা) and Theme
(System / Light / Dark) selectors. Choices apply immediately via
`AppLanguage.apply` and `AppPreferencesRepository.setThemeMode`.
Language preselected from device locale. Skip keeps the derived
defaults.

## Testing (TDD)

- resolver: Bn present → Bn; Bn blank/null → English
- `AppLanguage.current`: device-locale derivation; no SYSTEM
- `localizedDigits`: 0-9, multi-digit, zero, negative, English passthrough
- SeedData integrity: every built-in has non-blank `nameBn`,
  `pronunciationBn`, `translationBn`, `noteBn`; no residual `" — "` in
  either translation field
- backup: old file (no Bn fields) still imports

## Out of scope

Full onboarding redesign · Arabic (`ar`) locale · localizing AI
`benefitsText` (own language setting) · localizing shared-routine text ·
RTL (Bangla is LTR).
