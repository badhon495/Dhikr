package com.dhikr.app.core.database.seed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every built-in dhikr and preset routine must carry both language sides so a
 *  Bangla user never sees an English fallback on shipped content. */
class SeedDataLocalizationTest {

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
        // The old seed packed "Bangla — English" into one field. After the split
        // neither side should still contain the " — " separator.
        SeedData.builtInTasbih.forEach { t ->
            assertFalse("${t.id} translation still merged", t.translation.contains(" — "))
            assertFalse("${t.id} translationBn still merged", t.translationBn!!.contains(" — "))
        }
    }

    @Test
    fun english_pronunciation_is_not_bangla_script() {
        // Roman-script pronunciations only in the English field.
        SeedData.builtInTasbih.forEach { t ->
            assertFalse(
                "${t.id} pronunciation has Bangla characters",
                t.pronunciation.any { it in 'ঀ'..'৿' },
            )
        }
    }

    @Test
    fun preset_routines_have_bangla_names() {
        SeedData.presetRoutines.forEach { r ->
            assertTrue("${r.id} nameBn missing", !r.nameBn.isNullOrBlank())
        }
    }
}
