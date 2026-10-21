package com.dhikr.app.core.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.notifications.ReminderNotifications

/**
 * Dynamic launcher shortcuts: the first favourite routines, next to the
 * static "Count" / "Insights" ones in res/xml/shortcuts.xml. Launchers show
 * about four in total, so only [MAX_ROUTINES] are published.
 */
object AppShortcuts {

    private const val MAX_ROUTINES = 2

    fun update(context: Context, favoriteRoutines: List<RoutineWithSteps>, lang: AppLanguage) {
        val shortcuts = favoriteRoutines.take(MAX_ROUTINES).map { rws ->
            val name = rws.routine.displayName(lang)
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_SHORTCUT
                putExtra(ReminderNotifications.EXTRA_ROUTINE_ID, rws.routine.id)
            }
            ShortcutInfoCompat.Builder(context, "routine:${rws.routine.id}")
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_routine))
                .setIntent(intent)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    const val ACTION_SHORTCUT = "com.dhikr.app.action.SHORTCUT"
}
