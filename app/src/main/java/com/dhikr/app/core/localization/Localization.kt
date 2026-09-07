package com.dhikr.app.core.localization

import androidx.compose.runtime.staticCompositionLocalOf
import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.TasbihEntity

/**
 * The active content language, provided from `DhikrApp` as
 * `AppLanguage.current`. AppCompat recreates the Activity on a locale switch,
 * so the provided value is re-read on the recomposition that follows and there
 * is no need to observe a Flow. Reads outside composition (ViewModels, widget
 * renders) should call [AppLanguage.current] directly.
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

private fun pick(bn: String?, en: String, lang: AppLanguage): String =
    if (lang == AppLanguage.BANGLA) bn?.takeIf(String::isNotBlank) ?: en else en

fun TasbihEntity.displayName(lang: AppLanguage): String = pick(nameBn, name, lang)

fun TasbihEntity.displayPronunciation(lang: AppLanguage): String =
    pick(pronunciationBn, pronunciation, lang)

fun TasbihEntity.displayTranslation(lang: AppLanguage): String =
    pick(translationBn, translation, lang)

fun TasbihEntity.displayNote(lang: AppLanguage): String = pick(noteBn, note, lang)

fun RoutineEntity.displayName(lang: AppLanguage): String = pick(nameBn, name, lang)

private val BANGLA_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

/** Render an integer with Bangla digits in [AppLanguage.BANGLA], plain ASCII
 *  digits otherwise. For numbers built in Kotlin rather than through a
 *  `%d`-formatted string resource (those already localise under locale `bn`). */
fun Int.localizedDigits(lang: AppLanguage): String = toString().localizedDigits(lang)

/** Replace every ASCII digit in the string with its Bangla counterpart in
 *  [AppLanguage.BANGLA]. Leaves signs, separators and other characters intact. */
fun String.localizedDigits(lang: AppLanguage): String {
    if (lang != AppLanguage.BANGLA) return this
    return buildString(length) {
        for (c in this@localizedDigits) {
            append(if (c in '0'..'9') BANGLA_DIGITS[c - '0'] else c)
        }
    }
}
