package com.dhikr.app.core.counter

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dhikr.app.DhikrApplication
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.database.TasbihRepository
import com.dhikr.app.core.datastore.SessionRepository
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.widget.DhikrWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: each tap adds one count to the active tasbih without
 * opening the app. Mirrors the counter widget's [+] (same [WidgetCounter]
 * path, same no-routine rule), except when the Counter screen is alive — then
 * the tap goes into its ViewModel via [LiveCounter] so the in-memory session
 * doesn't overwrite it. With nothing countable, a tap opens the counter.
 */
class CounterTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    override fun onClick() {
        super.onClick()
        val live = LiveCounter.handle
        if (live != null) {
            live.tap()
            scope.launch { render() }
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.Default) { WidgetCounter.applyIncrement(applicationContext) }
            if (result == WidgetCounter.Result.NoOp) {
                openCounter()
            } else {
                DhikrWidgets.refreshAll(applicationContext)
                render()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val live = LiveCounter.handle
        val status = live?.status?.invoke() ?: withContext(Dispatchers.Default) { storedStatus() }
        tile.label = status?.title ?: getString(R.string.tile_counter_label)
        val subtitle = status?.subtitle ?: getString(R.string.tile_counter_no_session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.contentDescription = "${tile.label}, $subtitle"
        tile.state = if (status != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    /** Status of the persisted plain (non-routine) session, or null when the
     *  tile can't count it — the same cases [WidgetCounter.evaluate] no-ops on. */
    private suspend fun storedStatus(): LiveCounter.Status? {
        val app = applicationContext as DhikrApplication
        val session = SessionRepository(applicationContext).sessionFlow.first() ?: return null
        if (session.routineId != null) return null
        val tasbih = TasbihRepository(
            app.database.tasbihDao(),
            app.database.routineDao(),
            app.database.tasbihProgressDao(),
            app.database.sessionDao(),
        ).getById(session.activeDhikrId) ?: return null
        return LiveCounter.Status(
            title = tasbih.displayName(AppLanguage.current),
            subtitle = CounterDisplay.progress(session.count, session.lap, tasbih.lapTarget, tasbih.lapCount)
                .let { (count, target) -> "$count/$target" },
        )
    }

    // The PendingIntent overload only exists on API 34+; the Intent one is the
    // only option below that, so its deprecation can't be avoided there.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openCounter() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_COUNTER)
        }
        val launch = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        // Opening the app from the lock screen needs the device unlocked first;
        // counting itself (onClick's normal path) does not.
        if (isLocked) unlockAndRun { launch() } else launch()
    }
}
