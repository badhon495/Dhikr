# Daily-life Dua collection — design

## Intent and why

The app already has deep, well-sourced coverage of the big adhkar
occasions (morning, evening, after-salah, before-sleep, istighfar,
salawat, ruqyah, Qur'anic duas, praises) as bilingual, countable preset
Routines in `SeedData.kt`. What it has nothing for is the short,
situational dua said once around ordinary daily moments — leaving the
house, eating, travelling, dressing, rain, entering the bathroom, visiting
the sick, and so on. These don't belong in Routines (they aren't a
counted session) and nothing today gives a plain, browsable reference for
them.

This adds that missing slice: a new Dua reference screen covering
daily-life occasions, reachable from Home and from Settings, that fits
the existing dhikr/tasbih visual language. It ships pre-seeded with the
~20 occasions below, and also lets the user add their own dua entries
(own category, own text) — kept on a dedicated entity/screen separate
from Tasbih/Routine, not reusing their reminder/benefits/lap-count
machinery.

## Goals

- Cover the common daily-life dua occasions not already represented by
  an existing preset Routine. Confirmed against the current `SeedData.kt`
  (grep for wake/dress/house/travel/mosque/eat/bathroom/rain/thunder/
  wind/sick/grief/marriage): only "waking up" (`wake_alhamdu`, inside the
  Morning routine) already exists — everything else below is new.
  Occasions: dressing, leaving the house, entering the house, starting a
  journey, riding a vehicle, entering the mosque, leaving the mosque,
  before eating, after eating, before the bathroom, after the bathroom,
  seeing something pleasing, seeing something disliked, rain starting,
  hearing thunder, strong wind, visiting the sick, facing grief/calamity,
  feeling anger, a marriage proposal/wedding. That is 20 occasions —
  **one dua entry per occasion, not per sub-case** (so "before/after
  eating" is 2 entries, "entering/leaving the mosque" is 2 entries, etc.
  — each named occasion above is already singular). Final total: ~20
  entries, grouped into categories (House & Daily Life, Travel,
  Mosque, Eating, Nature, Social & Hardship — exact grouping left to the
  builder per the closing section).
- Each entry: Arabic (fully vowelled), Roman-script pronunciation,
  English translation, Bangla name + translation (the app is bilingual
  throughout — matches existing `*Bn` field convention), source
  (Qur'an ref or Hadith collection/book), and a repeat count (almost
  always 1, occasionally 3).
- Reference screen: grouped-by-category list, tap a category to expand
  or drill into its entries — read Arabic/pronunciation/translation
  inline, no navigation required to "read" a dua.
- Entries with repeatCount > 1 get the same inline tap-to-count affordance
  already used elsewhere (increment, visual fill/progress to target,
  resets don't need to persist across app restarts — this is a light,
  session-local tally, not a tracked Tasbih).
- Entry points: a card/row on Home, and a row in Settings (or a "More")
  leading to the Dua screen. No new bottom-nav tab.
- Bilingual throughout, following the existing `displayName` /
  `displayTranslation` resolver pattern (`Localization.kt`).
- User can add their own dua entries via an add button (FAB, matching
  `TasbihLibraryScreen`'s "+ New" affordance) on the Dua screen, and edit
  or delete any entry they added. Seeded entries are also editable/
  deletable (no `isBuiltIn` lock in this pass — matches Tasbih Library's
  existing permissiveness) but ship correct by default so most users
  never need to touch them.
- Add/edit form fields: category (picker: existing category or type a
  new one), name, arabic, translation, repeatCount — **required**.
  pronunciation, source — **optional** (a user typing their own dua may
  not have a transliteration or formal citation ready). Bangla fields
  (`nameBn`/`pronunciationBn`/`translationBn`) are optional on the form;
  the bilingual resolver already falls back to the English value when
  blank.

## Non-goals

- No new counted history: opening or tapping a dua does NOT write to
  `SessionEntity`/history/Insights/streak. This is reference content, not
  a trackable dhikr.
- No audio/recitation playback.
- No favoriting, reminders, or notifications for dua entries in this
  pass (add/edit/delete of the dua text itself is in scope; counting/
  reminder features on top of it are not).
- No duplication of content already covered by an existing preset
  Routine (morning/evening/after-salah/before-sleep/istighfar/salawat/
  ruqyah/praises/Qur'anic-duas) — this screen only holds the new
  daily-life categories (seed content; doesn't constrain what a user
  adds themselves).
- No backup/restore changes in this pass — user-added dua rows are new
  mutable state (unlike the read-only-seed version of this spec), but
  wiring them into the existing backup/restore JSON is left to a
  follow-up; flagged here so it isn't silently forgotten.

## Anti-goals

Would count as failure even if it technically works:

- Treating this as a second, parallel home for content that already
  lives in Routines (redundant maintenance burden, confusing "where do I
  find X" for users).
- Inaccurate or unsourced Arabic/translation — every entry must cite a
  real source (Qur'an ayah or a specific hadith collection/book), same
  authenticity bar `SeedData.kt`'s existing comments describe (weak/
  disputed-chain items get flagged or dropped, per the v15 precedent).
- A new bottom-nav tab — explicitly decided against; this is secondary
  reference content, not a primary tab-level feature.
- Reusing `TasbihEntity`/`TasbihEditorScreen` for dua, or leaking any
  reminder/daily-goal/benefits/lap field into the dua add/edit form —
  this is the exact problem this spec exists to avoid. Dua gets its own
  entity, DAO, and editor end to end.

## Constraints / decisions already made

- Bilingual (English + Bangla) — matches app convention.
- No new bottom-nav tab — entry via Home card + Settings row.
- Tap-to-count kept for entries with repeatCount > 1, but counts are
  session-local (plain `remember { mutableStateOf(0) }` per entry,
  reset on leaving the screen) — not persisted to Room, not part of
  history/Insights. This keeps the no-new-trackable-state goal intact
  while still giving the existing tactile affordance where a dua is
  genuinely repeated.

## Data model

New Room entity, deliberately NOT reusing `TasbihEntity` (no favoriting/
reminders/benefits/lap-progress fields it doesn't need, and mixing it into
the Tasbih table would pollute Tasbih Library search/sort/counters with
non-countable content):

```kotlin
@Entity(tableName = "dua")
data class DuaEntity(
    @PrimaryKey val id: String,
    val category: String,       // e.g. "travel", "eating", "house" -- groups the list
    val categoryOrder: Int,     // display order of the category
    val order: Int,             // display order within the category
    val name: String,
    val nameBn: String? = null,
    val arabic: String,
    val pronunciation: String? = null,
    val pronunciationBn: String? = null,
    val translation: String,
    val translationBn: String? = null,
    val source: String? = null,
    val repeatCount: Int = 1,
)
```

Seeded once at DB creation (new `AppDatabase` version bump + a
`SeedData.dua` list seeded the same way existing tasbih/routines are, via
`DuaDao.insertAll` on first run). After seeding, the table is fully
mutable through the add/edit/delete screen described below -- deliberately
NOT reusing `TasbihEntity`'s `isBuiltIn`/soft-delete scheme; a dua the
user deletes (seeded or their own) is just gone, same as Tasbih Library's
existing delete behavior.

`DuaDao` queries needed: `observeAll()` grouped by category order,
`upsert(entity: DuaEntity)`, `delete(id: String)`, `count()` (for the
seed-once check).

## Screens

**DuaScreen** (new, `feature/dua/DuaScreen.kt` + `DuaViewModel.kt`):
mirrors `TasbihLibraryScreen`'s visual language (card list, `ListRowShape`,
`colors.card`/`colors.surface`), with its own add/edit/delete chrome
(not read-only):

- Header: title "Dua" (+ Bangla "দোয়া"), add button
  (FAB or top-bar "+", matching `TasbihLibraryScreen`'s "+ New"
  affordance) that opens `DuaEditorScreen` in create mode.
- Sticky or inline category headers (Travel, Eating, House, Mosque,
  Nature, Social & Hardship, etc, plus any user-typed category).
- Each row: name + Bangla name, Arabic (right-aligned, same treatment as
  `TasbihRow`), pronunciation + translation + source always visible when
  present (no expand-on-tap -- entries are short enough not to need
  collapsing). **Row gesture (builder's call, settled here to avoid
  ambiguity):** tap on the row body opens `DuaEditorScreen` in edit mode
  -- there is no separate "counting session" to tap into (unlike
  Tasbih, where tap starts a count), so edit is the row's natural tap
  action. Long-press opens a delete confirmation (mirrors
  `TasbihLibraryScreen`'s long-press action menu, minus the
  favorite/share entries that don't apply here). The counter pill (next
  bullet) is a nested `clickable` Box inside the row, same pattern as
  Tasbih's nested favorite-heart icon -- tapping the pill increments the
  count and does NOT propagate to the row's open-editor tap.
- Entries with `repeatCount > 1` (expected to be rare -- most of these
  occasions are said once) show a small tap counter pill
  (`current/target`, increments on tap, same tactile/haptic feedback
  style as the Tasbih counter where reasonable) as a nested tap target
  inside the row (see above). Entries with `repeatCount == 1` show no
  pill.

**DuaEditorScreen** (new, `feature/dua/DuaEditorScreen.kt` +
`DuaEditorViewModel.kt`): single form, create and edit share it (null
`id` arg = create). Mirrors `TasbihEditorScreen`'s basic text-field
layout but with ONLY these fields -- no reminder section, no daily
goal/lap target, no AI benefits block:

- Category (text field with autocomplete/picker over existing distinct
  categories already in the table, or type a new one).
- Name, Arabic, Translation -- required; Save disabled until all three
  are non-blank.
- Pronunciation, Source -- optional text fields.
- Repeat count -- numeric stepper, default 1.
- Bangla fields (name/pronunciation/translation) -- optional, collapsed
  under an "Add Bangla" toggle/section (keeps the common case -- a
  user adding one quick dua in English -- short).
- Save writes via `DuaDao.upsert`; delete (edit mode only) removes via
  `DuaDao.delete(id)` and pops back to `DuaScreen`.

## Navigation / entry points

- `DhikrApp.kt`: two new routes -- `ROUTE_DUA = "dua"` (list, wired the
  same way `ROUTE_QIBLA` is: a non-tab destination pushed from
  elsewhere, back arrow returns) and `ROUTE_DUA_EDITOR = "dua/edit"`
  with an optional `duaId` nav arg (mirrors however
  `TasbihEditorScreen`'s route takes an optional id for create-vs-edit).
- **Home**: new small card below or near the existing Routines/Tasbih
  cards (`HomeScreen.kt` already has a card-list pattern -- add one more
  entry, e.g. "Daily Dua" with a short subtitle, `onClick` navigates to
  `ROUTE_DUA`).
- **Settings**: a row (near Qibla's entry point, which also opens a
  non-tab screen from Settings) that navigates to `ROUTE_DUA`.

## Localization

- New strings in both `values/strings.xml` and `values-bn/strings.xml`:
  screen title, category labels, empty/loading state (unlikely to be
  needed since seed is bundled, but keep consistent with other screens),
  Home card title/subtitle, Settings row label, add-button label, editor
  field labels (category/name/arabic/pronunciation/translation/source/
  repeat count/"Add Bangla"), save/delete confirmation strings.
- Content strings (name/arabic/pronunciation/translation/source) live in
  `SeedData.kt` as entity fields, not `strings.xml` — same convention as
  existing dhikr content.
- Reuse `displayName`/`displayPronunciation`/`displayTranslation`-style
  resolvers — add `DuaEntity` overloads in `Localization.kt` next to the
  existing `TasbihEntity`/`RoutineEntity` ones.

## What's left to the builder

- Exact final list of ~18-22 dua entries, their category grouping and
  category display order — curate from a reliable classical source
  (Hisnul Muslim / authentic hadith collections), citing source per
  entry; drop or flag anything with a weak chain, matching the v15
  precedent in `SeedData.kt`'s own history.
- Exact Room schema version bump number and migration comment text
  (follow the existing `AppDatabase.kt` numbered-comment convention).
- Icon choice for the Home card and Settings row (follow existing
  `NavIcons.kt`/`MiscIcons.kt` stroke style).
- Exact wording of all new string resources, in both languages.
- Whether the Home card always shows or only after onboarding — follow
  whatever the Routines/Tasbih cards already do (likely: always show).
