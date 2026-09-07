package com.dhikr.app.core.localization

import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.TasbihEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {

    private fun tasbih(
        name: String = "SubhanAllah",
        nameBn: String? = null,
        pronunciation: String = "SubhanAllah",
        pronunciationBn: String? = null,
        translation: String = "Glory be to Allah",
        translationBn: String? = null,
        note: String = "",
        noteBn: String? = null,
    ) = TasbihEntity(
        id = "t", name = name, nameBn = nameBn, arabic = "س",
        pronunciation = pronunciation, pronunciationBn = pronunciationBn,
        translation = translation, translationBn = translationBn,
        note = note, noteBn = noteBn,
        lapTarget = 33, lapCount = 1, isBuiltIn = true, createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun english_always_uses_the_base_field() {
        val t = tasbih(name = "SubhanAllah", nameBn = "সুবহানাল্লাহ")
        assertEquals("SubhanAllah", t.displayName(AppLanguage.ENGLISH))
    }

    @Test
    fun bangla_uses_the_bn_field_when_present() {
        val t = tasbih(
            name = "SubhanAllah", nameBn = "সুবহানাল্লাহ",
            pronunciation = "SubhanAllah", pronunciationBn = "সুবহানাল্লাহ",
            translation = "Glory be to Allah", translationBn = "আল্লাহ পবিত্র",
            note = "en note", noteBn = "বাংলা নোট",
        )
        assertEquals("সুবহানাল্লাহ", t.displayName(AppLanguage.BANGLA))
        assertEquals("সুবহানাল্লাহ", t.displayPronunciation(AppLanguage.BANGLA))
        assertEquals("আল্লাহ পবিত্র", t.displayTranslation(AppLanguage.BANGLA))
        assertEquals("বাংলা নোট", t.displayNote(AppLanguage.BANGLA))
    }

    @Test
    fun bangla_falls_back_to_english_when_bn_is_null_or_blank() {
        val nullBn = tasbih(name = "Evening Tasbih", nameBn = null)
        val blankBn = tasbih(name = "Evening Tasbih", nameBn = "   ")
        assertEquals("Evening Tasbih", nullBn.displayName(AppLanguage.BANGLA))
        assertEquals("Evening Tasbih", blankBn.displayName(AppLanguage.BANGLA))
    }

    @Test
    fun routine_display_name_resolves_like_tasbih() {
        val r = RoutineEntity(
            id = "morning", name = "Morning Dhikr", nameBn = "সকালের যিকর",
            isPreset = true, createdAt = 0L, updatedAt = 0L,
        )
        assertEquals("Morning Dhikr", r.displayName(AppLanguage.ENGLISH))
        assertEquals("সকালের যিকর", r.displayName(AppLanguage.BANGLA))
    }

    @Test
    fun localized_digits_maps_ascii_to_bangla_only_in_bangla() {
        assertEquals("1234567890", "1234567890".localizedDigits(AppLanguage.ENGLISH))
        assertEquals("১২৩৪৫৬৭৮৯০", "1234567890".localizedDigits(AppLanguage.BANGLA))
        assertEquals("৩৩", 33.localizedDigits(AppLanguage.BANGLA))
        assertEquals("33", 33.localizedDigits(AppLanguage.ENGLISH))
    }

    @Test
    fun localized_digits_preserves_non_digit_characters() {
        assertEquals("১২ / ৩৪", "12 / 34".localizedDigits(AppLanguage.BANGLA))
        assertEquals("-৫", (-5).localizedDigits(AppLanguage.BANGLA))
        assertEquals("০", 0.localizedDigits(AppLanguage.BANGLA))
    }
}
