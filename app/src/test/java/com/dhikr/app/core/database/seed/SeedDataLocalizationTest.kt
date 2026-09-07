package com.dhikr.app.core.database.seed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Localization contract for shipped seed content.
 *
 *  The core Tasbih library and the 99 names (Asma-ul-Husna) are *fully*
 *  bilingual: a distinct Bangla name, Roman-script pronunciation in the English
 *  field, Bangla script in the `*Bn` field, and a clean English/Bangla split of
 *  the translation.
 *
 *  The situational-dhikr batches (istighfar / morning / evening / before-sleep /
 *  praise / Qur'anic-dua / salawat / ruqyah / beneficial / surah) were authored
 *  before the bilingual split and only carry the Bangla side. They are allowed
 *  an English fallback for now (nameBn == name, Bangla script in `pronunciation`)
 *  and full localization of those entries is tracked follow-up work. Such an
 *  entry is identified by `nameBn == name`.
 */
class SeedDataLocalizationTest {

    private val fullyLocalized get() =
        SeedData.builtInTasbih.filter { it.nameBn != it.name }

    private val fallbackAllowed get() =
        SeedData.builtInTasbih.filter { it.nameBn == it.name }

    @Test
    fun fully_localized_tasbih_have_both_language_sides() {
        fullyLocalized.forEach { t ->
            assertTrue("${t.id} name blank", t.name.isNotBlank())
            assertTrue("${t.id} nameBn missing", !t.nameBn.isNullOrBlank())
            assertTrue("${t.id} pronunciation blank", t.pronunciation.isNotBlank())
            assertTrue("${t.id} pronunciationBn missing", !t.pronunciationBn.isNullOrBlank())
            assertTrue("${t.id} translation blank", t.translation.isNotBlank())
            assertTrue("${t.id} translationBn missing", !t.translationBn.isNullOrBlank())
        }
    }

    @Test
    fun fully_localized_translation_split_left_no_bilingual_residue() {
        // The old seed packed "Bangla — English" into one field. After the split
        // neither side should still contain the " — " separator.
        fullyLocalized.forEach { t ->
            assertFalse("${t.id} translation still merged", t.translation.contains(" — "))
            assertFalse("${t.id} translationBn still merged", t.translationBn!!.contains(" — "))
        }
    }

    @Test
    fun fully_localized_english_pronunciation_is_not_bangla_script() {
        // Roman-script pronunciations only in the English field.
        fullyLocalized.forEach { t ->
            assertFalse(
                "${t.id} pronunciation has Bangla characters",
                t.pronunciation.any { it in 'ঀ'..'৿' },
            )
        }
    }

    @Test
    fun fallback_entries_still_carry_non_blank_content() {
        // Even a fallback entry must render: a name, a recitation guide and a
        // meaning on both sides (the Bangla side may equal the English side).
        fallbackAllowed.forEach { t ->
            assertTrue("${t.id} name blank", t.name.isNotBlank())
            assertTrue("${t.id} nameBn blank", !t.nameBn.isNullOrBlank())
            assertTrue("${t.id} pronunciation blank", t.pronunciation.isNotBlank())
            assertTrue("${t.id} pronunciationBn blank", !t.pronunciationBn.isNullOrBlank())
            assertTrue("${t.id} translation blank", t.translation.isNotBlank())
            assertTrue("${t.id} translationBn blank", !t.translationBn.isNullOrBlank())
        }
    }

    @Test
    fun preset_routines_have_bangla_names() {
        // The five preset routines surfaced on Home carry a Bangla name; the
        // situational presets fall back to the English name for now.
        val localizedPresets = setOf("morning", "evening", "after_salah", "before_sleep", "asma_ul_husna")
        SeedData.presetRoutines.forEach { r ->
            if (r.id in localizedPresets) {
                assertTrue("${r.id} nameBn missing", !r.nameBn.isNullOrBlank())
            }
        }
    }
}
