package com.dhikr.app.core.database.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Localization contract for shipped seed content.
 *
 *  Every built-in dhikr and preset routine carries both language sides: a
 *  Bangla name, a Bangla-script recitation guide, and a clean English/Bangla
 *  split of the translation and note.
 *
 *  The situational-dhikr batches (istighfar / morning / evening / before-sleep /
 *  praise / Qur'anic-dua / salawat / ruqyah / beneficial / surah) are fully
 *  localized on the Bangla side but their English-side `pronunciation` still
 *  holds the Bangla rendering — a faithful Roman transliteration is tracked
 *  follow-up work. Those ids live in [SeedData.pendingRomanTransliterationIds]
 *  and are exempt only from the "English pronunciation is Roman script" check.
 */
class SeedDataLocalizationTest {

    private val bengali = 'ঀ'..'৿'

    @Test
    fun every_builtin_tasbih_has_both_language_sides() {
        SeedData.builtInTasbih.forEach { t ->
            assertTrue("${t.id} name blank", t.name.isNotBlank())
            assertTrue("${t.id} nameBn missing", !t.nameBn.isNullOrBlank())
            assertTrue("${t.id} pronunciation blank", t.pronunciation.isNotBlank())
            assertTrue("${t.id} pronunciationBn missing", !t.pronunciationBn.isNullOrBlank())
            assertTrue("${t.id} translation blank", t.translation.isNotBlank())
            assertTrue("${t.id} translationBn missing", !t.translationBn.isNullOrBlank())
        }
    }

    @Test
    fun translation_split_left_no_bilingual_residue() {
        // The pre-split seed packed "Bangla — English" into one field. After the
        // builder's split neither side should still open with that separator.
        SeedData.builtInTasbih.forEach { t ->
            assertFalse("${t.id} translation still merged", t.translation.startsWith(" — "))
            assertFalse("${t.id} translationBn still merged", t.translationBn!!.endsWith(" — "))
        }
    }

    @Test
    fun nameBn_is_bangla_script() {
        SeedData.builtInTasbih.forEach { t ->
            assertTrue(
                "${t.id} nameBn has no Bangla characters: ${t.nameBn}",
                t.nameBn!!.any { it in bengali },
            )
        }
    }

    @Test
    fun pronunciationBn_is_bangla_script() {
        SeedData.builtInTasbih.forEach { t ->
            assertTrue(
                "${t.id} pronunciationBn has no Bangla characters",
                t.pronunciationBn!!.any { it in bengali },
            )
        }
    }

    @Test
    fun english_pronunciation_is_roman_script_except_pending() {
        SeedData.builtInTasbih
            .filter { it.id !in SeedData.pendingRomanTransliterationIds }
            .forEach { t ->
                assertFalse(
                    "${t.id} pronunciation has Bangla characters",
                    t.pronunciation.any { it in bengali },
                )
            }
    }

    @Test
    fun pending_transliteration_ids_all_exist() {
        val builtInIds = SeedData.builtInTasbih.map { it.id }.toSet()
        val unknown = SeedData.pendingRomanTransliterationIds - builtInIds
        assertEquals("pendingRomanTransliterationIds names dhikr that do not exist: $unknown", emptySet<String>(), unknown)
    }

    @Test
    fun preset_routines_have_bangla_names() {
        // The five preset routines surfaced on Home carry a Bangla name; the
        // situational presets fall back to the English name for now.
        val localizedPresets = setOf("morning", "evening", "after_salah", "before_sleep", "asma_ul_husna")
        SeedData.presetRoutines
            .filter { it.id in localizedPresets }
            .forEach { r ->
                assertTrue("${r.id} nameBn missing", !r.nameBn.isNullOrBlank())
            }
    }
}
