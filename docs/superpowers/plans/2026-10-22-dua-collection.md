# Daily-life Dua Collection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bilingual "Dua" reference-and-editable screen covering ~20 daily-life occasions (travel, eating, house, mosque, nature, hardship), fully separate from Tasbih/Routine (own entity, DAO, screen, editor), reachable from Home and Settings, with add/edit/delete support.

**Architecture:** New `DuaEntity`/`DuaDao` (separate Room table, seeded once, fully mutable after), a `DuaRepository` wrapping the DAO, a `DuaViewModel` exposing the seeded+user list grouped by category, a `DuaScreen` composable styled like `TasbihLibraryScreen` with an add FAB, and a `DuaEditorScreen`/`DuaEditorViewModel` (create+edit share one form) with only dua-relevant fields — no reminder/daily-goal/benefits/lap fields from `TasbihEntity`. Entries with `repeatCount > 1` get a session-local (non-persisted) tap counter nested inside the row.

**Tech Stack:** Kotlin, Jetpack Compose, Room, existing `AppDatabase`/`SeedData` conventions.

**Spec:** `docs/superpowers/specs/2026-10-22-dua-collection-design.md`

## Global Constraints

- Bilingual throughout: every content field has an English value and an optional `*Bn` Bangla value, following `TasbihEntity`'s field convention; resolver falls back to English when Bangla is blank.
- No new bottom-nav tab — entry via a Home card and a Settings row only.
- No history/Insights/streak writes from this screen; tap counters are `remember { mutableStateOf(0) }`, not persisted to Room.
- `DuaEntity` must NOT reuse or reference `TasbihEntity` fields — no `isBuiltIn`, no reminder/dailyGoal/benefits/lapTarget/lapCount/isFavorite. This is the core reason this feature exists (user complaint: dua editor was leaking routine fields).
- Required fields on add/edit: category, name, arabic, translation. Optional: pronunciation, source, all `*Bn` fields, repeatCount (defaults to 1).
- Seeded entries are fully editable/deletable, no soft-delete, no re-seed-on-missing — deletion is permanent, matching Tasbih Library's existing delete behavior.
- Every seed entry cites a real source (Qur'an ayah or named hadith collection/book) — no unsourced seed text. (User-added entries may leave source blank.)
- `AppDatabase` version bump follows the existing numbered-comment convention in `AppDatabase.kt` (currently at `version = 29`; this plan uses `version = 30`).
- Row gesture: tap opens editor (edit mode); long-press opens delete confirmation; the repeat-count pill is a nested `clickable` Box that does not propagate taps to the row.

## Review Focus

- **Empty/missing Bangla field on a hand-typed entry** — if any `*Bn` field is left null, the resolver (`pick()` in `Localization.kt`) must fall back to the English value rather than showing blank text. Covered by Task 3's resolver test.
- **`repeatCount == 1` entries must render with no counter pill and no nested tap target** — this is the overwhelming majority of entries; a regression here would make the screen feel broken. Covered by Task 4's UI test.
- **Tap counter resets on navigating away and back** — session-local `remember` state scoped to the composable; leaving `DuaScreen` and returning must show the counter back at 0. Covered by Task 4's UI test.
- **Category ordering is stable and matches `categoryOrder`/`order` fields, not insertion order** — `DuaDao.observeAll()` must `ORDER BY categoryOrder, order`. Covered by Task 1's DAO test.
- **Save is disabled until name, arabic, and translation are all non-blank** — a reviewer tapping Save on an empty or partial form must not create a broken row. Covered by Task 5's editor test.
- **Deleting an entry removes it permanently and does not reappear on next app launch** — since seeding is gated on `count() == 0`, not per-row, a deleted seeded entry must stay gone. Covered by Task 1's DAO test and Task 6's end-to-end note.

---

## File Structure

- Create: `app/src/main/java/com/dhikr/app/core/database/entity/DuaEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/dao/DuaDao.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt` — add `DuaEntity`, `duaDao()`, bump version to 30
- Create: `app/src/main/java/com/dhikr/app/core/database/seed/DuaSeedData.kt` — the 20 seed entries
- Create: `app/src/main/java/com/dhikr/app/core/database/DuaRepository.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/localization/Localization.kt` — add `DuaEntity.displayName/displayPronunciation/displayTranslation` resolvers
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaScreen.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaEditorViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaEditorScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt` — add `ROUTE_DUA`, `ROUTE_DUA_EDITOR`, composable destinations, Home/Settings nav callbacks
- Modify: `app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt` — add Dua card + `onOpenDua` param
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt` — add Dua row + `onOpenDua` param
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml` — new UI strings
- Test: `app/src/test/java/com/dhikr/app/core/database/DuaDaoTest.kt`
- Test: `app/src/test/java/com/dhikr/app/core/database/seed/DuaSeedDataTest.kt`
- Test: `app/src/test/java/com/dhikr/app/feature/dua/DuaViewModelTest.kt`
- Test: `app/src/test/java/com/dhikr/app/feature/dua/DuaEditorViewModelTest.kt`
- Test: `app/src/androidTest/java/com/dhikr/app/feature/dua/DuaScreenTest.kt` (or local-test equivalent — verified in Task 1 Step 1)

---

### Task 1: `DuaEntity` + `DuaDao` + database wiring

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/entity/DuaEntity.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/dao/DuaDao.kt`
- Modify: `app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt`
- Test: `app/src/test/java/com/dhikr/app/core/database/DuaDaoTest.kt`

**Interfaces:**
- Produces: `DuaEntity(id: String, category: String, categoryOrder: Int, order: Int, name: String, nameBn: String? = null, arabic: String, pronunciation: String? = null, pronunciationBn: String? = null, translation: String, translationBn: String? = null, source: String? = null, repeatCount: Int = 1)`; `DuaDao.observeAll(): Flow<List<DuaEntity>>`; `DuaDao.insertAll(List<DuaEntity>)`; `DuaDao.upsert(entity: DuaEntity)`; `DuaDao.delete(id: String)`; `DuaDao.count(): Int`; `DuaDao.getById(id: String): DuaEntity?`; `AppDatabase.duaDao(): DuaDao`.

- [ ] **Step 1: Check whether an `androidTest` source set / instrumented Room test already exists in the project**

Run: `find app/src -maxdepth 2 -type d -name androidTest` and `grep -rl "Room.inMemoryDatabaseBuilder" app/src`. Note the result — it decides whether this task's and Task 4/5's UI tests run as instrumented tests or (if none exist) as local Robolectric/JVM tests using the same pattern as the existing `PrayerTimesTest.kt`.

- [ ] **Step 2: Write the failing DAO tests**

```kotlin
@Test
fun observeAll_ordersByCategoryThenOrder() = runTest {
    val dao = database.duaDao()
    dao.insertAll(listOf(
        DuaEntity(id = "b", category = "travel", categoryOrder = 2, order = 1, name = "B", arabic = "ب", translation = "b"),
        DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "ا", translation = "a"),
    ))
    val result = dao.observeAll().first()
    assertEquals(listOf("a", "b"), result.map { it.id })
}

@Test
fun delete_removesEntryPermanently() = runTest {
    val dao = database.duaDao()
    dao.insertAll(listOf(DuaEntity(id = "x", category = "house", categoryOrder = 1, order = 1, name = "X", arabic = "x", translation = "x")))
    dao.delete("x")
    assertEquals(0, dao.count())
    assertNull(dao.getById("x"))
}

@Test
fun upsert_insertsNewAndUpdatesExisting() = runTest {
    val dao = database.duaDao()
    val entry = DuaEntity(id = "y", category = "house", categoryOrder = 1, order = 1, name = "Y", arabic = "y", translation = "y")
    dao.upsert(entry)
    assertEquals("Y", dao.getById("y")?.name)
    dao.upsert(entry.copy(name = "Y2"))
    assertEquals(1, dao.count())
    assertEquals("Y2", dao.getById("y")?.name)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*DuaDaoTest*"` (adjust to the instrumented-test Gradle task if Step 1 found this project uses `androidTest`)
Expected: FAIL — `DuaEntity`/`DuaDao` not defined.

- [ ] **Step 4: Implement `DuaEntity` in `DuaEntity.kt`**

Mirror `TasbihEntity`'s `@Immutable @Entity(tableName = "dua")` style. Fields exactly as given in Interfaces above, `@PrimaryKey val id: String`.

- [ ] **Step 5: Implement `DuaDao` in `DuaDao.kt`**

```kotlin
@Dao
interface DuaDao {
    @Query("SELECT * FROM dua ORDER BY categoryOrder ASC, `order` ASC")
    fun observeAll(): Flow<List<DuaEntity>>

    @Query("SELECT * FROM dua WHERE id = :id")
    suspend fun getById(id: String): DuaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(dua: List<DuaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DuaEntity)

    @Query("DELETE FROM dua WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM dua")
    suspend fun count(): Int
}
```
(`order` is a SQL reserved word — backtick it in the query as shown.)

- [ ] **Step 6: Wire into `AppDatabase.kt`**

Add `DuaEntity::class` to the `entities` array, add a numbered `// v30:` comment following the existing style ("added dua table + 20 daily-life dua seeded on first run, fully editable/deletable by user. No hand migration — fallbackToDestructiveMigration rebuilds + reseeds."), bump `version = 30`, add `abstract fun duaDao(): DuaDao`.

- [ ] **Step 7: Run tests to verify they pass**

Run: same command as Step 3.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/entity/DuaEntity.kt app/src/main/java/com/dhikr/app/core/database/dao/DuaDao.kt app/src/main/java/com/dhikr/app/core/database/AppDatabase.kt app/src/test/java/com/dhikr/app/core/database/DuaDaoTest.kt
git commit -m "feat: add Dua entity, DAO and database wiring"
```

---

### Task 2: Seed content (20 daily-life dua) + `DuaRepository`

**Files:**
- Create: `app/src/main/java/com/dhikr/app/core/database/seed/DuaSeedData.kt`
- Create: `app/src/main/java/com/dhikr/app/core/database/DuaRepository.kt`
- Modify: `app/src/main/java/com/dhikr/app/DhikrApplication.kt` (seed-on-create hook — find and follow the existing `SeedData` seeding call site)
- Test: `app/src/test/java/com/dhikr/app/core/database/seed/DuaSeedDataTest.kt`

**Interfaces:**
- Consumes: `DuaEntity`, `DuaDao` from Task 1.
- Produces: `DuaSeedData.all: List<DuaEntity>` (20 entries); `DuaRepository(dao: DuaDao)` with `fun observeAll(): Flow<List<DuaEntity>>`, `suspend fun seedIfEmpty()`, `suspend fun getById(id: String): DuaEntity?`, `suspend fun upsert(entity: DuaEntity)`, `suspend fun delete(id: String)`.

- [ ] **Step 1: Write the failing seed-content test**

```kotlin
@Test
fun seedData_has20UniqueEntriesAllValidAndSourced() {
    val entries = DuaSeedData.all
    assertEquals(20, entries.size)
    assertEquals(20, entries.map { it.id }.toSet().size) // unique ids
    entries.forEach { entry ->
        assertTrue("${entry.id} missing nameBn", !entry.nameBn.isNullOrBlank())
        assertTrue("${entry.id} missing translationBn", !entry.translationBn.isNullOrBlank())
        assertTrue("${entry.id} missing source", !entry.source.isNullOrBlank())
        assertTrue("${entry.id} missing arabic", entry.arabic.isNotBlank())
    }
}

@Test
fun seedData_excludesAlreadyCoveredWakingUp() {
    assertTrue(DuaSeedData.all.none { it.id == "wake_alhamdu" || it.category == "waking" })
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*DuaSeedDataTest*"`
Expected: FAIL — `DuaSeedData` not defined.

- [ ] **Step 3: Implement `DuaSeedData.kt` with the 20 entries below**

Use a `private fun dua(...)` builder mirroring `SeedData.dhikr()`'s bilingual-field style (explicit `nameBn`/`pronunciationBn`/`translationBn` per entry, all from Hisnul Muslim / standard hadith sources). Categories and order:

**Category "house" (categoryOrder=1):** dressing (order=1), leaving the house (2), entering the house (3)
**Category "travel" (categoryOrder=2):** starting a journey (1), riding a vehicle (2)
**Category "mosque" (categoryOrder=3):** entering the mosque (1), leaving the mosque (2)
**Category "eating" (categoryOrder=4):** before eating (1), after eating (2)
**Category "bathroom" (categoryOrder=5):** before the bathroom (1), after the bathroom (2)
**Category "seeing" (categoryOrder=6):** seeing something pleasing (1), seeing something disliked (2)
**Category "nature" (categoryOrder=7):** rain starting (1), hearing thunder (2), strong wind (3)
**Category "hardship" (categoryOrder=8):** visiting the sick (1), facing grief/calamity (2), feeling anger (3), marriage proposal/wedding (4)

That is 3+2+2+2+2+2+3+4 = 20 entries. Each entry (id, name, arabic, pronunciation, translation, source, repeatCount — all also need nameBn/pronunciationBn/translationBn):

1. `dressing` — "Bismillah" (when putting on a garment) / "Allahumma laka al-hamd anta kasawtanihi, as'aluka min khairihi wa khairi ma suni'a lah, wa a'udhu bika min sharrihi wa sharri ma suni'a lah" — Abu Dawud 4023, Tirmidhi 1767 — repeatCount=1
2. `leaving_house` — "Bismillahi, tawakkaltu 'ala Allah, wa la hawla wa la quwwata illa billah" (In the name of Allah, I place my trust in Allah, there is no power and no strength except with Allah) — Abu Dawud 5095, Tirmidhi 3426 — repeatCount=1
3. `entering_house` — "Bismillahi walajna, wa bismillahi kharajna, wa 'ala Allahi rabbina tawakkalna" then greet those inside — Abu Dawud 5096 — repeatCount=1
4. `travel_start` — "Allahu akbar" x3 then "Subhanal-ladhi sakhkhara lana hadha wa ma kunna lahu muqrinin, wa inna ila rabbina lamunqalibun" — Sahih Muslim 1342 (the traveller's dua, from Qur'an 43:13-14) — repeatCount=1
5. `vehicle` — "Bismillah" on boarding, "Alhamdulillah" when seated — Sahih Muslim 1342 — repeatCount=1
6. `entering_mosque` — "Allahumma iftah li abwaba rahmatik" (O Allah, open the gates of Your mercy for me) — Sahih Muslim 713 — repeatCount=1
7. `leaving_mosque` — "Allahumma inni as'aluka min fadlik" (O Allah, I ask You from Your bounty) — Sahih Muslim 713 — repeatCount=1
8. `before_eating` — "Bismillah" (if forgotten at the start: "Bismillahi awwalahu wa akhirahu") — Abu Dawud 3767, Tirmidhi 1858 — repeatCount=1
9. `after_eating` — "Alhamdu lillahil-ladhi at'amani hadha, wa razaqanihi min ghayri hawlin minni wa la quwwah" — Abu Dawud 3850, Tirmidhi 3458 — repeatCount=1
10. `before_bathroom` — "Bismillah, Allahumma inni a'udhu bika minal khubthi wal khaba'ith" — Sahih al-Bukhari 142, Sahih Muslim 375 — repeatCount=1
11. `after_bathroom` — "Ghufranak" (I seek Your forgiveness) — Abu Dawud 30, Tirmidhi 7 — repeatCount=1
12. `seeing_pleasing` — "Alhamdu lillah" (All praise is for Allah) — Sunan Ibn Majah 3803 (on seeing anything pleasing, the Prophet (saw) would prostrate or say this) — repeatCount=1
13. `seeing_disliked` — "Alhamdu lillahi 'ala kulli hal" (All praise is for Allah in every circumstance) — Sunan Ibn Majah 3803 — repeatCount=1
14. `rain_starting` — "Allahumma sayyiban nafi'an" (O Allah, [make it] a beneficial rain cloud) — Sahih al-Bukhari 1032 — repeatCount=1
15. `thunder` — "Subhanal-ladhi yusabbihur ra'du bihamdihi wal mala'ikatu min khifatih" (Glory be to Him Whom the thunder glorifies, and the angels too, out of fear of Him) — reported from Abdullah ibn al-Zubayr, Musnad Ahmad; al-Adab al-Mufrad 723 — repeatCount=1
16. `strong_wind` — "Allahumma inni as'aluka khairaha, wa khaira ma fiha, wa khaira ma ursilat bih, wa a'udhu bika min sharriha, wa sharri ma fiha, wa sharri ma ursilat bih" — Sahih Muslim 899 — repeatCount=1
17. `visiting_sick` — "As'alullahal 'azima an yashfiyak" x7 (I ask the Mighty Allah to cure you) — Abu Dawud 3106, Tirmidhi 2083 — repeatCount=7
18. `grief_calamity` — "Inna lillahi wa inna ilayhi raji'un. Allahumma ajurni fi musibati wakhluf li khairan minha" — Sahih Muslim 918 — repeatCount=1
19. `feeling_anger` — "A'udhu billahi minash-shaytanir-rajim" (I seek refuge in Allah from Satan the accursed) — Sahih al-Bukhari 6115, Sahih Muslim 2610 — repeatCount=1
20. `marriage_wedding` — "Barakallahu laka wa baraka 'alayka wa jama'a baynakuma fi khair" (May Allah bless you, shower His blessings upon you, and join you together in goodness) — Abu Dawud 2130, Tirmidhi 1091 — repeatCount=1

Write accurate fully-vowelled Arabic for each from the cited source, Bangla name/pronunciation/translation for each following `SeedData.kt`'s existing bilingual convention (Bangla script pronunciation, Bangla translation). If a citation cannot be verified while implementing, flag it in a code comment rather than inventing a reference, matching the v15 precedent of dropping weak-chain items.

- [ ] **Step 4: Implement `DuaRepository.kt`**

```kotlin
class DuaRepository(private val dao: DuaDao) {
    fun observeAll(): Flow<List<DuaEntity>> = dao.observeAll()
    suspend fun seedIfEmpty() {
        if (dao.count() == 0) dao.insertAll(DuaSeedData.all)
    }
    suspend fun getById(id: String): DuaEntity? = dao.getById(id)
    suspend fun upsert(entity: DuaEntity) = dao.upsert(entity)
    suspend fun delete(id: String) = dao.delete(id)
}
```

- [ ] **Step 5: Wire `seedIfEmpty()` into `DhikrApplication.kt`'s existing seed-on-create path**

Find the call site that seeds `SeedData` into `TasbihDao`/`RoutineDao` on database creation; add `DuaRepository(database.duaDao()).seedIfEmpty()` alongside it, in the same coroutine scope.

- [ ] **Step 6: Run test to verify it passes**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/database/seed/DuaSeedData.kt app/src/main/java/com/dhikr/app/core/database/DuaRepository.kt app/src/main/java/com/dhikr/app/DhikrApplication.kt app/src/test/java/com/dhikr/app/core/database/seed/DuaSeedDataTest.kt
git commit -m "feat: seed 20 daily-life dua entries"
```

---

### Task 3: Localization resolvers + `DuaViewModel`

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/core/localization/Localization.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaViewModel.kt`
- Test: `app/src/test/java/com/dhikr/app/feature/dua/DuaViewModelTest.kt`

**Interfaces:**
- Consumes: `DuaEntity` (Task 1), `DuaRepository.observeAll()` (Task 2), `AppLanguage` / `pick()` pattern (existing `Localization.kt`).
- Produces: `DuaEntity.displayName(lang): String`, `.displayPronunciation(lang): String?`, `.displayTranslation(lang): String`; `DuaViewModel(repository: DuaRepository)` exposing `val uiState: StateFlow<DuaUiState>` where `data class DuaUiState(val categories: List<DuaCategoryUi>)` and `data class DuaCategoryUi(val category: String, val entries: List<DuaEntity>)`; `DuaViewModel.Factory(repository: DuaRepository)`.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
@Test
fun displayName_fallsBackToEnglishWhenBanglaBlank() {
    val entry = DuaEntity(id = "x", category = "c", categoryOrder = 1, order = 1, name = "English", nameBn = null, arabic = "ع", translation = "t")
    assertEquals("English", entry.displayName(AppLanguage.BANGLA))
}

@Test
fun displayPronunciation_returnsNullWhenBothBlank() {
    val entry = DuaEntity(id = "x", category = "c", categoryOrder = 1, order = 1, name = "N", arabic = "ع", translation = "t", pronunciation = null)
    assertNull(entry.displayPronunciation(AppLanguage.ENGLISH))
}
```

- [ ] **Step 2: Write the failing `DuaViewModel` grouping test**

```kotlin
@Test
fun uiState_groupsEntriesByCategoryInOrder() = runTest {
    val repo = FakeDuaRepository(listOf(
        DuaEntity(id = "a", category = "house", categoryOrder = 1, order = 1, name = "A", arabic = "ا", translation = "a"),
        DuaEntity(id = "b", category = "travel", categoryOrder = 2, order = 1, name = "B", arabic = "ب", translation = "b"),
    ))
    val viewModel = DuaViewModel(repo)
    val state = viewModel.uiState.first { it.categories.isNotEmpty() }
    assertEquals(listOf("house", "travel"), state.categories.map { it.category })
}
```
(Add a minimal `FakeDuaRepository` in the test file implementing `observeAll()` from a `MutableStateFlow`, and no-op `upsert`/`delete`/`getById`.)

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*Localization*" --tests "*DuaViewModelTest*"`
Expected: FAIL — resolvers/`DuaViewModel` not defined.

- [ ] **Step 4: Implement the three resolvers in `Localization.kt`**

Add directly below the existing `TasbihEntity` resolvers, same `pick(bn, en, lang)` call pattern:
```kotlin
fun DuaEntity.displayName(lang: AppLanguage): String = pick(nameBn, name, lang)
fun DuaEntity.displayPronunciation(lang: AppLanguage): String? = pronunciation?.let { pick(pronunciationBn, it, lang) }
fun DuaEntity.displayTranslation(lang: AppLanguage): String = pick(translationBn, translation, lang)
```

- [ ] **Step 5: Implement `DuaViewModel.kt`**

`uiState` is `repository.observeAll().map { entries -> DuaUiState(entries.groupBy { it.category }.toSortedByCategoryOrder()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DuaUiState(emptyList()))` — group while preserving the category order already guaranteed by `DuaDao.observeAll()`'s `ORDER BY`, not re-sorting alphabetically (use `LinkedHashMap`-preserving `groupBy` over the already-ordered list, then map to `DuaCategoryUi`).

- [ ] **Step 6: Run tests to verify they pass**

Run: same command as Step 3.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/core/localization/Localization.kt app/src/main/java/com/dhikr/app/feature/dua/DuaViewModel.kt app/src/test/java/com/dhikr/app/feature/dua/DuaViewModelTest.kt
git commit -m "feat: add Dua localization resolvers and view model"
```

---

### Task 4: `DuaScreen` composable (list, add FAB, edit/delete row gestures, counter pill)

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaScreen.kt`
- Test: `app/src/androidTest/java/com/dhikr/app/feature/dua/DuaScreenTest.kt` (or the local-test equivalent per Task 1 Step 1's finding)

**Interfaces:**
- Consumes: `DuaViewModel.uiState` (Task 3), `DuaEntity.displayName/displayPronunciation/displayTranslation` (Task 3), `colors.card`/`colors.surface`/`ListRowShape`/`PillShape` (existing `DhikrTheme`/`Shape.kt`), `headingSemantics()`/`minTapTarget()` (existing `ui/A11y.kt`).
- Produces: `@Composable fun DuaScreen(viewModel: DuaViewModel, onBack: () -> Unit, onAddDua: () -> Unit, onEditDua: (id: String) -> Unit)`.

- [ ] **Step 1: Write the failing UI test — static row has no counter pill and tap opens editor**

```kotlin
@Test
fun entryWithRepeatCountOne_showsNoCounterPillAndTapOpensEditor() {
    var editedId: String? = null
    // compose DuaScreen with a fake single-entry uiState (id = "b", repeatCount = 1), onEditDua = { editedId = it }
    composeTestRule.onNodeWithText("B").assertExists()
    composeTestRule.onAllNodesWithTag("dua_counter_pill").assertCountEquals(0)
    composeTestRule.onNodeWithTag("dua_row_b").performClick()
    assertEquals("b", editedId)
}
```

- [ ] **Step 2: Write the failing UI test — repeated entry's counter increments without opening the editor, and resets on recomposition**

```kotlin
@Test
fun entryWithRepeatCountGreaterThanOne_tapIncrementsLocalCounterWithoutOpeningEditor() {
    var editedId: String? = null
    // compose DuaScreen with a fake entry (id = "c", repeatCount = 7), onEditDua = { editedId = it }
    composeTestRule.onNodeWithTag("dua_counter_pill").assertTextEquals("0/7")
    composeTestRule.onNodeWithTag("dua_counter_pill").performClick()
    composeTestRule.onNodeWithTag("dua_counter_pill").assertTextEquals("1/7")
    assertNull(editedId) // pill tap must not propagate to the row's onEditDua
}
```

- [ ] **Step 3: Write the failing UI test — add FAB invokes `onAddDua`**

```kotlin
@Test
fun addButton_invokesOnAddDua() {
    var added = false
    // compose DuaScreen with onAddDua = { added = true }
    composeTestRule.onNodeWithTag("dua_add_button").performClick()
    assertTrue(added)
}
```

- [ ] **Step 4: Write the failing UI test — translation text is never truncated**

```kotlin
@Test
fun translationText_hasNoMaxLinesOrEllipsis() {
    // compose DuaScreen with the longest seeded entry (marriage_wedding)
    composeTestRule.onNodeWithTag("dua_translation_marriage_wedding").assertIsDisplayed()
    // assert full translation substring is present, not truncated with "…"
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: the instrumented or local test command identified in Task 1 Step 1, filtered to `DuaScreenTest`.
Expected: FAIL — `DuaScreen` not defined.

- [ ] **Step 6: Implement `DuaScreen.kt`**

Structure, following `TasbihLibraryScreen.kt`'s visual conventions:
- `Column` with header `Text(stringResource(R.string.dua_screen_title))` + back arrow (`onBack`), `colors.bg` background, same `16.dp` horizontal padding. Add button in the header (top-bar "+") or a `FloatingActionButton`, `Modifier.testTag("dua_add_button")`, `onClick = onAddDua`.
- `LazyColumn` iterating `state.categories`: a category header `Text` (category label from a `stringResource` lookup keyed by `DuaCategoryUi.category`), then each category's `entries` as rows.
- Row composable `DuaRow(entry: DuaEntity, lang: AppLanguage, onEdit: () -> Unit)`: outer `Box` with `colors.card` background, `ListRowShape`, `Modifier.testTag("dua_row_${entry.id}").combinedClickable(onClick = onEdit, onLongClick = { /* show delete confirm */ })` (mirrors `TasbihLibraryScreen`'s row gesture pattern — tap edits, long-press deletes). Shows `displayName`, `arabic` (right-aligned like `TasbihRow`), `displayPronunciation` (if non-null), and `displayTranslation` with no `maxLines`/`overflow` (satisfies Review Focus), tagged `Modifier.testTag("dua_translation_${entry.id}")` on the translation `Text`, plus a small `Text` for `source` (if non-null).
- When `entry.repeatCount > 1`: render a small pill (`PillShape`, `colors.sageSoft`) nested inside the row's `Box` as its own `clickable` child (NOT part of the outer `combinedClickable` — same pattern as Tasbih's nested favorite-heart icon, so the tap does not propagate to `onEdit`), holding `var count by remember(entry.id) { mutableStateOf(0) }` (local, resets whenever the composable leaves composition — satisfies Review Focus), `Text("$count/${entry.repeatCount}")`, `Modifier.testTag("dua_counter_pill").clickable { count = (count + 1).coerceAtMost(entry.repeatCount) }`.
- Delete confirmation on long-press: a simple `AlertDialog` ("Delete this dua?" / Cancel / Delete) that calls `viewModel` to delete on confirm (wire a `deleteDua(id: String)` function on `DuaViewModel` calling `repository.delete(id)` in `viewModelScope`, following whatever delete pattern `TasbihLibraryScreen`'s view model already uses).

- [ ] **Step 7: Run tests to verify they pass**

Run: same command as Step 5.
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/dua/DuaScreen.kt app/src/main/java/com/dhikr/app/feature/dua/DuaViewModel.kt app/src/androidTest/java/com/dhikr/app/feature/dua/DuaScreenTest.kt
git commit -m "feat: add Dua screen UI with add/edit/delete gestures"
```

---

### Task 5: `DuaEditorScreen` + `DuaEditorViewModel` (add/edit form)

**Files:**
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaEditorViewModel.kt`
- Create: `app/src/main/java/com/dhikr/app/feature/dua/DuaEditorScreen.kt`
- Test: `app/src/test/java/com/dhikr/app/feature/dua/DuaEditorViewModelTest.kt`
- Test: `app/src/androidTest/java/com/dhikr/app/feature/dua/DuaEditorScreenTest.kt` (or local-test equivalent per Task 1 Step 1)

**Interfaces:**
- Consumes: `DuaRepository.getById/upsert/delete` (Task 2), `DuaEntity` (Task 1).
- Produces: `DuaEditorViewModel(repository: DuaRepository, duaId: String?)` exposing `val uiState: StateFlow<DuaEditorUiState>` where `data class DuaEditorUiState(val category: String = "", val name: String = "", val nameBn: String = "", val arabic: String = "", val pronunciation: String = "", val pronunciationBn: String = "", val translation: String = "", val translationBn: String = "", val source: String = "", val repeatCount: Int = 1, val isSaveEnabled: Boolean = false, val isEditMode: Boolean = false)`; mutators `onCategoryChange`, `onNameChange`, `onArabicChange`, `onTranslationChange`, `onPronunciationChange`, `onSourceChange`, `onRepeatCountChange`, `onBanglaFieldChange(field, value)`; `fun save(onDone: () -> Unit)`; `fun delete(onDone: () -> Unit)`; `DuaEditorViewModel.Factory(repository: DuaRepository, duaId: String?)`; `@Composable fun DuaEditorScreen(viewModel: DuaEditorViewModel, onBack: () -> Unit)`.

- [ ] **Step 1: Write the failing `DuaEditorViewModel` tests — save gating**

```kotlin
@Test
fun isSaveEnabled_falseUntilNameArabicTranslationAllNonBlank() = runTest {
    val viewModel = DuaEditorViewModel(FakeDuaRepository(emptyList()), duaId = null)
    assertFalse(viewModel.uiState.value.isSaveEnabled)
    viewModel.onNameChange("Name")
    viewModel.onArabicChange("ع")
    assertFalse(viewModel.uiState.value.isSaveEnabled) // translation still blank
    viewModel.onTranslationChange("Translation")
    assertTrue(viewModel.uiState.value.isSaveEnabled)
}
```

- [ ] **Step 2: Write the failing `DuaEditorViewModel` test — edit mode loads existing entry**

```kotlin
@Test
fun editMode_loadsExistingEntryFields() = runTest {
    val existing = DuaEntity(id = "x", category = "house", categoryOrder = 1, order = 1, name = "X", arabic = "x", translation = "xt", source = "src")
    val viewModel = DuaEditorViewModel(FakeDuaRepository(listOf(existing)), duaId = "x")
    val state = viewModel.uiState.first { it.name == "X" }
    assertEquals("house", state.category)
    assertEquals("src", state.source)
    assertTrue(state.isEditMode)
}
```

- [ ] **Step 3: Write the failing `DuaEditorViewModel` test — save writes via repository with a generated id in create mode**

```kotlin
@Test
fun save_createMode_upsertsNewEntityWithGeneratedId() = runTest {
    val repo = FakeDuaRepository(emptyList())
    val viewModel = DuaEditorViewModel(repo, duaId = null)
    viewModel.onCategoryChange("custom")
    viewModel.onNameChange("My Dua")
    viewModel.onArabicChange("دعاء")
    viewModel.onTranslationChange("My translation")
    var done = false
    viewModel.save { done = true }
    assertTrue(done)
    assertEquals(1, repo.upsertedEntities.size)
    assertEquals("My Dua", repo.upsertedEntities.first().name)
}
```
(Add a minimal `FakeDuaRepository` in the test file tracking `upsertedEntities`/`deletedIds`, implementing `getById` from its seed list, `observeAll()` as a no-op `MutableStateFlow`.)

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*DuaEditorViewModelTest*"`
Expected: FAIL — `DuaEditorViewModel` not defined.

- [ ] **Step 5: Implement `DuaEditorViewModel.kt`**

Constructor loads existing entity via `repository.getById(duaId)` into `uiState` when `duaId != null` (sets `isEditMode = true`, maps nullable fields to empty strings for the text fields). `isSaveEnabled` recomputed on every mutator call as `name.isNotBlank() && arabic.isNotBlank() && translation.isNotBlank()`. `save()` builds a `DuaEntity` from current state (new `id` via `java.util.UUID.randomUUID().toString()` in create mode, existing `duaId` in edit mode; blank optional fields mapped back to `null`; `category`/`categoryOrder`/`order` — for a user-typed new category, `categoryOrder` is `existing max categoryOrder + 1`, `order` is `0`; for an existing category, append at `existing max order within that category + 1` — compute from the current `observeAll()` snapshot), calls `repository.upsert(entity)` in `viewModelScope`, then `onDone()`. `delete()` calls `repository.delete(duaId!!)` then `onDone()` (only callable in edit mode).

- [ ] **Step 6: Run tests to verify they pass**

Run: same command as Step 4.
Expected: PASS.

- [ ] **Step 7: Write the failing `DuaEditorScreen` UI test — Save button disabled state mirrors `isSaveEnabled`**

```kotlin
@Test
fun saveButton_disabledUntilRequiredFieldsFilled() {
    // compose DuaEditorScreen with create-mode viewModel
    composeTestRule.onNodeWithTag("dua_editor_save").assertIsNotEnabled()
    // type into name/arabic/translation fields via their test tags
    composeTestRule.onNodeWithTag("dua_editor_save").assertIsEnabled()
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: the instrumented or local test command identified in Task 1 Step 1, filtered to `DuaEditorScreenTest`.
Expected: FAIL — `DuaEditorScreen` not defined.

- [ ] **Step 9: Implement `DuaEditorScreen.kt`**

Single-column form, mirroring `TasbihEditorScreen.kt`'s basic text-field layout but with ONLY: category (`OutlinedTextField` with `testTag("dua_editor_category")`, autocomplete suggestions optional — plain text field is acceptable), name (required, `testTag("dua_editor_name")`), arabic (required, `testTag("dua_editor_arabic")`), translation (required, `testTag("dua_editor_translation")`), pronunciation (optional), source (optional), repeat count (numeric stepper, default 1), and a collapsible "Add Bangla" section exposing `nameBn`/`pronunciationBn`/`translationBn` fields. Top bar: title ("Add Dua" / "Edit Dua" per `uiState.isEditMode`), back arrow (`onBack`), Save button `testTag("dua_editor_save")` bound to `uiState.isSaveEnabled`, calling `viewModel.save(onBack)`. In edit mode only, a Delete action (overflow menu or icon button) calling `viewModel.delete(onBack)`. No reminder section, no daily-goal/lap-target fields, no AI benefits block.

- [ ] **Step 10: Run tests to verify they pass**

Run: same command as Step 8.
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dhikr/app/feature/dua/DuaEditorViewModel.kt app/src/main/java/com/dhikr/app/feature/dua/DuaEditorScreen.kt app/src/test/java/com/dhikr/app/feature/dua/DuaEditorViewModelTest.kt app/src/androidTest/java/com/dhikr/app/feature/dua/DuaEditorScreenTest.kt
git commit -m "feat: add Dua add/edit/delete editor screen"
```

---

### Task 6: Navigation wiring + Home/Settings entry points + strings

**Files:**
- Modify: `app/src/main/java/com/dhikr/app/DhikrApp.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-bn/strings.xml`

**Interfaces:**
- Consumes: `DuaScreen`, `DuaViewModel.Factory` (Task 3/4), `DuaEditorScreen`, `DuaEditorViewModel.Factory` (Task 5), `DuaRepository` (Task 2), `app.database.duaDao()` (Task 1).
- Produces: `ROUTE_DUA = "dua"`, `ROUTE_DUA_EDITOR = "dua/edit?duaId={duaId}"` constants; `HomeScreen(..., onOpenDua: () -> Unit)`; `SettingsScreen(..., onOpenDua: () -> Unit)`.

- [ ] **Step 1: Add string resources**

In both `strings.xml` files add: `dua_screen_title` ("Dua" / "দোয়া"), `home_dua_card_title`, `home_dua_card_subtitle`, `settings_dua_open` (mirroring `settings_qibla`/`settings_qibla_open`'s existing pattern), one label string per category (`dua_category_house`, `dua_category_travel`, `dua_category_mosque`, `dua_category_eating`, `dua_category_bathroom`, `dua_category_seeing`, `dua_category_nature`, `dua_category_hardship`), `dua_add_title` ("Add Dua"), `dua_edit_title` ("Edit Dua"), `dua_editor_category_label`, `dua_editor_name_label`, `dua_editor_arabic_label`, `dua_editor_pronunciation_label`, `dua_editor_translation_label`, `dua_editor_source_label`, `dua_editor_repeat_count_label`, `dua_editor_add_bangla_toggle`, `dua_editor_save`, `dua_delete_confirm_title`, `dua_delete_confirm_body`, `dua_delete_confirm_action` — English and Bangla text for each.

- [ ] **Step 2: Add `ROUTE_DUA`/`ROUTE_DUA_EDITOR` and the composable destinations in `DhikrApp.kt`**

Add `private const val ROUTE_DUA = "dua"` and `private const val ROUTE_DUA_EDITOR = "dua/edit?duaId={duaId}"` near the other `ROUTE_*` constants (mirror however `TasbihEditorScreen`'s route takes an optional id arg for create-vs-edit). Add a `composable(ROUTE_DUA) { ... }` block mirroring `ROUTE_QIBLA`'s block: construct `DuaRepository(app.database.duaDao())` via `remember`, get `DuaViewModel` via `viewModel(factory = DuaViewModel.Factory(duaRepository))`, render `DuaScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onAddDua = { navController.navigate("dua/edit") }, onEditDua = { id -> navController.navigate("dua/edit?duaId=$id") })`. Add a `composable(ROUTE_DUA_EDITOR, arguments = listOf(navArgument("duaId") { nullable = true; defaultValue = null }))` block constructing `DuaEditorViewModel` via `viewModel(factory = DuaEditorViewModel.Factory(duaRepository, duaId))`, render `DuaEditorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })`.

- [ ] **Step 3: Wire `onOpenDua` through to `HomeScreen` and `SettingsScreen` call sites in `DhikrApp.kt`**

In the `ROUTE_HOME` composable block, add `onOpenDua = { navController.navigate(ROUTE_DUA) }` to the `HomeScreen(...)` call. In the `ROUTE_SETTINGS` composable block, add `onOpenDua = { navController.navigate(ROUTE_DUA) }` to the `SettingsScreen(...)` call.

- [ ] **Step 4: Add a Dua card to `HomeScreen.kt`**

Add `onOpenDua: () -> Unit` parameter. Add a card below the existing Routines/Tasbih-library cards, following whatever existing card composable (`RoutineHomeCard` or the library-card pattern around line 237) is simplest to mirror structurally: title from `R.string.home_dua_card_title`, subtitle from `R.string.home_dua_card_subtitle`, `Modifier.clickable(role = Role.Button) { onOpenDua() }`.

- [ ] **Step 5: Add a Dua row to `SettingsScreen.kt`**

Add `onOpenDua: () -> Unit` parameter, threaded down the same way `onOpenQibla` already is (check `PrayerSettingsSection.kt`'s row at line ~125 for the exact `clickable(role = Role.Button, onClick = ...)` pattern) — place it as its own row/section near the Qibla row, not inside `PrayerSettingsSection` (Dua isn't prayer-specific), using `R.string.dua_screen_title` as the row label and `R.string.settings_dua_open` as the trailing hint text.

- [ ] **Step 6: Build the app and manually verify navigation and CRUD**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Then (manual, per the project's UI-change convention) install on a device/emulator: tap the Home Dua card and the Settings Dua row, confirm both open `DuaScreen`; tap the add button, create a dua, confirm it appears in its category; tap a row, edit it, confirm the change persists; long-press a row, delete it, confirm it's gone and does not reappear after killing and relaunching the app (Review Focus: permanent delete).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dhikr/app/DhikrApp.kt app/src/main/java/com/dhikr/app/feature/home/HomeScreen.kt app/src/main/java/com/dhikr/app/feature/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-bn/strings.xml
git commit -m "feat: wire Dua screen and editor into navigation, Home and Settings"
```

---

## Self-Review

**1. Spec coverage:** Data model incl. upsert/delete (Task 1), seed content with authenticity bar (Task 2), bilingual resolvers + view model (Task 3), screen UI incl. add FAB/tap-to-edit/long-press-to-delete/nested counter pill (Task 4), editor form with required/optional field split and delete action (Task 5), Home/Settings entry points + strings (Task 6). The spec's explicit non-goals (no history writes, no favoriting/reminders, no backup changes, no new tab, no `TasbihEntity` reuse) are structurally satisfied — no task touches `SessionDao`, `BackupRepository`, `ReminderScheduler`, `DhikrBottomNav`, or any `TasbihEntity` field. Covered.

**2. Step scan:** Each step names an exact signature, file, and test assertion; algorithm bodies are only given where the spec fixes exact content (the 20 entries' sources/translations in Task 2) or where a non-obvious choice exists (Task 3 Step 5's order-preserving `groupBy`; Task 5 Step 5's category-ordering-on-insert rule, which the spec leaves implicit and a plan must pin down since it's not derivable from the entity shape alone).

**3. Type consistency:** `DuaEntity` fields (Task 1) match usage in `DuaSeedData`/`DuaRepository` (Task 2), `Localization.kt` resolvers (Task 3), `DuaScreen`/`DuaRow` (Task 4), and `DuaEditorViewModel`/`DuaEditorScreen` (Task 5) — `category`, `categoryOrder`, `order`, `repeatCount`, `source`, `pronunciation` (nullable) spelled identically throughout. `DuaViewModel.uiState`/`DuaUiState`/`DuaCategoryUi` names from Task 3 match Task 4's consumption. `DuaRepository.getById/upsert/delete` signatures from Task 2 match Task 5's consumption exactly.

**4. Review Focus:** All six lines have an owning task and test (Bangla fallback → Task 3 Step 1; no-counter-pill on single-count entries → Task 4 Step 1; counter reset → Task 4 Step 2; category ordering → Task 1 Step 2; Save gating → Task 5 Step 1/Step 7; permanent delete → Task 1 Step 2 + Task 6 Step 6's manual check).

**5. Proportion:** The 20-entry content list in Task 2 is necessarily long (it's the spec's core content, not restated code), but every other task stays at signature/test-name granularity; no task hands over a full implementation body beyond what the spec itself fixes (field lists, gesture assignment).
