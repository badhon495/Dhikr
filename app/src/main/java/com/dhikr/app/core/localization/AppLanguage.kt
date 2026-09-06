package com.dhikr.app.core.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The two UI-language choices offered in Settings. Persistence and the actual
 * locale switch go through [AppCompatDelegate.setApplicationLocales] — with
 * `autoStoreLocales` in the manifest, AppCompat stores the choice itself and
 * re-applies it on the next launch (API 33+ delegates to the platform). So there
 * is no DataStore key for this; [current] reads the live locale list back.
 *
 * There is no "system" option: the app ships English and Bangla only, and the
 * user picks one explicitly on first run (see the onboarding Language step). A
 * fresh install whose locale list is still empty falls back to [BANGLA] on a
 * Bangla device and [ENGLISH] everywhere else.
 */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        /** The active choice. An empty application locale list means the user
         *  has not picked yet — derive from the device language so the very
         *  first frame is already in a sensible language. */
        val current: AppLanguage
            get() {
                val locales = AppCompatDelegate.getApplicationLocales()
                val language = if (locales.isEmpty) {
                    Locale.getDefault().language
                } else {
                    locales[0]?.language
                }
                return entries.firstOrNull { it.tag == language } ?: ENGLISH
            }

        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.tag),
            )
        }
    }
}
