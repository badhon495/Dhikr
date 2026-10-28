package com.dhikr.app.feature.counter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhikr.app.MainActivity
import com.dhikr.app.R
import com.dhikr.app.core.counter.AutoCounterSensorListener
import com.dhikr.app.core.counter.CounterDisplay
import com.dhikr.app.core.counter.LiveCounter
import com.dhikr.app.core.datastore.CounterScript
import com.dhikr.app.core.datastore.CounterTextSize
import com.dhikr.app.core.datastore.DEFAULT_DIM_BRIGHTNESS_LEVEL
import com.dhikr.app.core.datastore.HapticMode
import com.dhikr.app.core.haptics.rememberHaptics
import com.dhikr.app.core.localization.LocalAppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.localization.displayNote
import com.dhikr.app.core.localization.displayPronunciation
import com.dhikr.app.core.utilities.MIN_SCREEN_BRIGHTNESS
import com.dhikr.app.core.utilities.currentSystemBrightnessFraction
import com.dhikr.app.core.localization.localizedDigits
import com.dhikr.app.ui.COUNTER_TAP_AREA_TEST_TAG
import com.dhikr.app.ui.ClampedFontScale
import com.dhikr.app.ui.LocalReducedMotion
import com.dhikr.app.ui.Motion
import com.dhikr.app.ui.LocalSnackbar
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.ArabicLineStyle
import com.dhikr.app.ui.theme.CounterCountLongTextStyle
import com.dhikr.app.ui.theme.CounterCountStyle
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape
import com.dhikr.app.ui.theme.PronunciationLongTextStyle
import com.dhikr.app.ui.theme.PronunciationPrimaryStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.delay

private const val LONG_TEXT_THRESHOLD = 90

@OptIn(ExperimentalFoundationApi::class)
private const val RAPID_TAP_MS = 220L

@Composable
fun CounterScreen(
    viewModel: CounterViewModel,
    // From the Haptics setting (DataStore) — hoisted in DhikrApp and passed in.
    // OFF: no vibration. EVERY_TAP: buzz on every count. LAP_ONLY: buzz only
    // when a lap completes.
    hapticMode: HapticMode = HapticMode.EVERY_TAP,
    // From the "Counter shows" setting. ARABIC shows the Arabic line only;
    // PRONUNCIATION shows the pronunciation line only.
    counterScript: CounterScript = CounterScript.PRONUNCIATION,
    // From the "Count on wrist flick" setting (off by default, plan.md §40).
    autoCounterEnabled: Boolean = false,
    // From the "Counter text size" setting.
    counterTextSize: CounterTextSize = CounterTextSize.DEFAULT,
    // From the "Keep screen awake" setting (plan.md §34, off by default).
    keepScreenOn: Boolean = false,
    // From the "Count with volume buttons" setting (off by default).
    volumeKeyCounting: Boolean = false,
    // App-wide screen dim (MainActivity applies the actual brightness change);
    // this screen only shows/toggles it and, via long-press, lets the user set
    // dimBrightnessLevel.
    dimScreenEnabled: Boolean = false,
    onToggleDimScreen: () -> Unit = {},
    dimBrightnessLevel: Float = DEFAULT_DIM_BRIGHTNESS_LEVEL,
    onDimBrightnessLevelChange: (Float) -> Unit = {},
    onBack: () -> Unit,
    // Reports the lock toggle up to DhikrApp, which owns the Scaffold the
    // bottom nav bar lives in — CounterScreen has no reach to it directly.
    onLockedChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    // B2: collected separately so a 1s timer tick recomposes only the nodes
    // that read it (top-bar label, summary dialogs), not the whole screen.
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val colors = DhikrTheme.colors
    val haptics = rememberHaptics()
    val reducedMotion = LocalReducedMotion.current
    val snackbar = LocalSnackbar.current
    val lockedHint = stringResource(R.string.counter_locked_hint)
    val resetDoneText = stringResource(R.string.counter_reset_done)
    val undoText = stringResource(R.string.feedback_undo)
    val showLockedHint = { snackbar?.show(lockedHint) }
    var showSessionSummary by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showDimLevelDialog by remember { mutableStateOf(false) }

    // The on-screen back chevron is gated on !state.locked, but the system
    // back gesture/button bypasses that entirely — it doesn't go through
    // the chevron's clickable at all. Intercept and swallow it here so a
    // pocket-touch (or the OS back gesture) can't leave a locked session.
    BackHandler(enabled = state.locked) { showLockedHint() }

    // Locking the counter goes beyond this screen's own controls: it also
    // hides the app's bottom nav bar (DhikrApp owns that Scaffold, so it
    // can't read CounterViewModel directly) and drops the system status/
    // navigation bars into immersive mode so a pocket-touch can't background
    // the app or pull down notifications mid-session. The nav-bar side is
    // reported upward here; the system-bar side is handled locally below.
    LaunchedEffect(state.locked) {
        onLockedChanged(state.locked)
    }

    val view = LocalView.current
    DisposableEffect(state.locked, view) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (state.locked) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // Always restore system bars on leaving the screen, regardless of
            // lock state, so a locked session left via the system back
            // gesture/task switcher never strands the app in immersive mode.
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Flush the in-flight session to disk on ON_STOP so a session is never lost
    // to process death inside the ViewModel's 500ms save debounce window.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onEnterForeground()
                Lifecycle.Event.ON_STOP -> {
                    // Freeze the session timer first — elapsed time counts only
                    // active foreground dhikr, not time spent with the app away.
                    viewModel.onEnterBackground()
                    // Refresh placed widgets only after the session persist actually
                    // completes, so the provider's DataStore read sees the latest
                    // count instead of racing the async write.
                    val appContext = view.context.applicationContext
                    viewModel.flushSession {
                        com.dhikr.app.core.widget.DhikrWidgets.refreshAll(appContext)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Log the in-progress session to permanent History when this composable
    // actually leaves composition (back navigation, navigating elsewhere) —
    // a different trigger than ON_STOP above, which also fires on a mere
    // app-background/foreground cycle (the composable stays in composition
    // through that). Kept as a separate DisposableEffect so the two triggers
    // are never conflated.
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.logAndClearOnLeave()
        }
    }

    // Auto counter (plan.md §40): registers the accelerometer listener only
    // while the setting is on, a session is actually loaded, and the counter
    // isn't locked — locking exists specifically to stop accidental counts
    // (§35), so a pocketed, locked phone must not auto-count from motion.
    // Unregistered on every key change (including the composable leaving
    // composition), so nothing samples the sensor once this screen is gone.
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(autoCounterEnabled, state.sessionReady, state.locked, viewModel) {
        val active = autoCounterEnabled && state.sessionReady && !state.locked
        val sensorListener = if (active) AutoCounterSensorListener(appContext) else null
        if (sensorListener != null) {
            sensorListener.start { viewModel.onTap() }
        }
        onDispose {
            sensorListener?.stop()
        }
    }

    // Route Quick Settings tile taps into this live session (see LiveCounter)
    // for as long as the screen exists — including while backgrounded, since
    // the ViewModel's in-memory state would otherwise overwrite a count the
    // tile wrote straight to the session store.
    val liveState by rememberUpdatedState(state)
    val liveLang by rememberUpdatedState(LocalAppLanguage.current)
    DisposableEffect(viewModel) {
        val handle = LiveCounter.Handle(
            tap = { viewModel.onTap() },
            status = {
                val s = liveState
                LiveCounter.Status(
                    title = s.dhikr.displayName(liveLang),
                    subtitle = CounterDisplay.progress(s.count, s.lap, s.dhikr.lapTarget, s.totalLaps)
                        .let { (count, target) -> "$count/$target" },
                )
            },
        )
        LiveCounter.handle = handle
        onDispose {
            if (LiveCounter.handle === handle) LiveCounter.handle = null
        }
    }

    // Keep screen awake (plan.md §34): only while this screen is showing and
    // the session is actually running, never app-wide. View-scoped, so leaving
    // the screen (or pausing) hands the timeout straight back to the system.
    DisposableEffect(keepScreenOn, state.running, view) {
        view.keepScreenOn = keepScreenOn && state.running
        onDispose { view.keepScreenOn = false }
    }

    // Volume-button counting. Deliberately still active while locked: the lock
    // guards the touchscreen against pocket touches, and a hardware button
    // press is a deliberate action — lock + volume keys is the intended way to
    // count without looking at the screen. Disabled while previewing a past
    // routine step (read-only) and once the routine is complete.
    val currentHapticMode by rememberUpdatedState(hapticMode)
    DisposableEffect(volumeKeyCounting, state.sessionReady, state.isViewingPastStep, state.isRoutineComplete, view) {
        val activity = view.context as? MainActivity
        val active = volumeKeyCounting && state.sessionReady &&
            !state.isViewingPastStep && !state.isRoutineComplete
        activity?.volumeKeyHandler = if (active) {
            {
                if (currentHapticMode == HapticMode.EVERY_TAP) haptics.tick()
                viewModel.onTap()
            }
        } else {
            null
        }
        onDispose { activity?.volumeKeyHandler = null }
    }

    val lang = LocalAppLanguage.current

    // Only one script is shown, per the "Counter shows" setting.
    val showArabic = counterScript == CounterScript.ARABIC
    val scriptText = if (showArabic) state.dhikr.arabic else state.dhikr.displayPronunciation(lang)
    val isLongText = scriptText.length > LONG_TEXT_THRESHOLD
    // The "Counter text size" setting also grows the ring and the count number
    // (never shrinks them), capped at 1.2x so the ring still fits a phone width.
    val ringScale = counterTextSize.scale.coerceIn(1f, 1.2f)
    val ringSize = (if (isLongText) 178.dp else 252.dp) * ringScale
    val baseCountStyle = if (isLongText) CounterCountLongTextStyle else CounterCountStyle
    val countStyle = if (ringScale == 1f) baseCountStyle else baseCountStyle.copy(
        fontSize = baseCountStyle.fontSize * ringScale,
    )
    val pronunciationStyle = when {
        isLongText -> PronunciationLongTextStyle
        else -> PronunciationPrimaryStyle
    }

    // Quick pop of the ring on every registered tap / lap rollover — skipped
    // entirely under Reduce motion.
    val countScale = remember { Animatable(1f) }
    // Taps closer than RAPID_TAP_MS apart (a fast dhikr rhythm) skip the pop and
    // the digit slide, which otherwise stack into constant jitter.
    var rapidTaps by remember { mutableStateOf(false) }
    var lastCountChangeMs by remember { mutableStateOf(0L) }
    LaunchedEffect(state.count, state.lap, reducedMotion) {
        val nowMs = android.os.SystemClock.elapsedRealtime()
        rapidTaps = nowMs - lastCountChangeMs < RAPID_TAP_MS
        lastCountChangeMs = nowMs
        if (reducedMotion || rapidTaps) {
            countScale.snapTo(1f)
        } else {
            countScale.snapTo(1.07f)
            countScale.animateTo(1f, animationSpec = tween(110))
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = min(1f, state.progressFraction),
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(Motion.STANDARD_MS, easing = Motion.StandardEasing)
        },
        label = "ring-progress",
    )

    // On every lap rollover the ring fills to full, holds a beat, then visibly
    // unwinds back down to 0 before the new lap starts filling again. For a
    // 1-per-lap dhikr every tap is a rollover, so every tap gets a fresh
    // fill -> unwind sweep (otherwise progressFraction is stuck near 0 and
    // nothing visibly moves).
    val ringFlourish = remember { Animatable(0f) }
    var flourishing by remember { mutableStateOf(false) }
    var showLapTargetNumber by remember { mutableStateOf(false) }
    var flourishLap by remember { mutableStateOf(state.lap) }
    var flourishBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.lap) {
        if (!state.sessionReady) return@LaunchedEffect
        if (!flourishBaselineSynced) {
            flourishLap = state.lap
            flourishBaselineSynced = true
            return@LaunchedEffect
        }
        val rolledOver = state.lap == flourishLap + 1
        flourishLap = state.lap
        if (rolledOver && !reducedMotion) {
            flourishing = true
            showLapTargetNumber = true
            // Snap to full (the tap that completed the lap) and hold briefly.
            ringFlourish.snapTo(1f)
            delay(140)
            showLapTargetNumber = false
            // Unwind the fill all the way back to empty.
            ringFlourish.animateTo(
                0f,
                animationSpec = tween(Motion.PULSE_MS, easing = Motion.StandardEasing),
            )
            flourishing = false
        }
    }
    // While the rollover flourish plays, the ring shows the flourish sweep alone
    // (full -> 0) so the unwind is visible; otherwise it tracks real progress.
    val ringProgress = if (flourishing) ringFlourish.value else animatedProgress

    // Subtle lap-complete feedback (plan.md §14): the progress arc tints from
    // terra toward sage and back over ~300ms when the lap number ticks up.
    val lapPulse = remember { Animatable(0f) }
    var lastLap by remember { mutableStateOf(state.lap) }
    var lapBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.lap) {
        // The ViewModel emits Empty (lap 0, sessionReady false) first, then
        // settles to the restored session in a single jump. Wait for that
        // settle and sync the baseline off it — otherwise a session restored
        // at exactly lap 1 looks like a fresh 0 -> 1 rollover to the +1 guard
        // below and buzzes every time the screen is (re)entered, e.g. via the
        // "Count" nav tab.
        if (!state.sessionReady) return@LaunchedEffect
        if (!lapBaselineSynced) {
            lastLap = state.lap
            lapBaselineSynced = true
            return@LaunchedEffect
        }
        // Exactly +1 — a real lap rollover only ever advances by one.
        if (state.lap == lastLap + 1) {
            // LAP_ONLY: this is the only place the counter vibrates. EVERY_TAP
            // also gets a stronger buzz here to mark the lap boundary.
            if (hapticMode != HapticMode.OFF) haptics.lapTick()
            if (!reducedMotion) {
                lapPulse.snapTo(1f)
                lapPulse.animateTo(0f, animationSpec = tween(Motion.PULSE_MS))
            }
        }
        lastLap = state.lap
    }

    // A routine step boundary is a lap boundary too, but each step runs its own
    // single-lap engine so `state.lap` never rolls over across steps — the guard
    // above misses it. Buzz here when the routine's current-step index advances,
    // and when the whole routine completes, so every step transition gets the
    // same emphatic feedback as a lap rollover.
    var lastRoutineStepIndex by remember { mutableStateOf(state.currentRoutineStepIndex) }
    var routineStepBaselineSynced by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionReady, state.currentRoutineStepIndex, state.isRoutineComplete) {
        if (!state.sessionReady) return@LaunchedEffect
        if (!routineStepBaselineSynced) {
            lastRoutineStepIndex = state.currentRoutineStepIndex
            routineStepBaselineSynced = true
            return@LaunchedEffect
        }
        val stepAdvanced = state.currentRoutineStepIndex == lastRoutineStepIndex + 1
        if (stepAdvanced || state.isRoutineComplete) {
            if (hapticMode != HapticMode.OFF) haptics.lapTick()
        }
        lastRoutineStepIndex = state.currentRoutineStepIndex
    }
    val progressArcColor = lerp(colors.terra, colors.sage, lapPulse.value)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // ---- Top bar: back, dhikr name + session label, lock toggle ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(48.dp),
        ) {
            val backLabel = stringResource(R.string.counter_back_content_description)
            IconButtonTooltip(label = backLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // Back navigation is blocked while the counter is locked, so a
                        // stray pocket-touch can't drop out of an active session.
                        .clickable { if (state.locked) showLockedHint() else onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = backChevronIcon(),
                        contentDescription = backLabel,
                        tint = if (state.locked) colors.faint else colors.dim,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            val sessionLabelDescription = stringResource(R.string.counter_session_label_content_description)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = state.dhikr.displayName(lang),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 1,
                )
                Text(
                    text = if (state.routineSteps.isNotEmpty()) {
                        val total = state.routineSteps.size
                        val done = if (state.isRoutineComplete) {
                            total
                        } else {
                            state.currentRoutineStepIndex.coerceIn(0, total)
                        }
                        "$done/$total  ·  ${formatDuration(elapsedSeconds)}"
                    } else {
                        formatSessionLabel(elapsedSeconds, state.totalCount)
                    },
                    fontSize = 12.sp,
                    color = colors.faint,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(
                            enabled = state.sessionReady,
                            role = Role.Button,
                        ) { showSessionSummary = true }
                        .semantics {
                            contentDescription = sessionLabelDescription
                        },
                )
            }
            val notesLabel = stringResource(R.string.counter_notes_content_description)
            IconButtonTooltip(label = notesLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // Read-only, so it stays gated on sessionReady/locked like the
                        // rest of the row rather than the note's own emptiness — an
                        // empty note still opens the dialog (with a placeholder line)
                        // rather than looking broken/unresponsive.
                        .clickable(enabled = state.sessionReady) {
                            if (state.locked) showLockedHint() else showNotesDialog = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = noteIcon(),
                        contentDescription = notesLabel,
                        tint = if (state.locked) colors.faint else {
                            if (state.dhikr.displayNote(lang).isNotBlank()) colors.dim else colors.faint
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            val lockLabel = stringResource(R.string.counter_lock_content_description)
            IconButtonTooltip(label = lockLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        // The lock toggle is otherwise always enabled — it is the only
                        // way back out of the locked state — but is still gated on
                        // sessionReady (finding #2): Room's seed data may not have
                        // loaded yet, and there is nothing to lock/unlock before then.
                        .clickable(enabled = state.sessionReady) { viewModel.onToggleLock() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = lockIcon(state.locked),
                        contentDescription = lockLabel,
                        tint = if (state.locked) colors.terra else colors.faint,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            val dimLabel = if (dimScreenEnabled) {
                stringResource(R.string.counter_dim_screen_off_content_description)
            } else {
                stringResource(R.string.counter_dim_screen_on_content_description)
            }
            IconButtonTooltip(label = dimLabel) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { onToggleDimScreen() },
                            // Long-press opens the level slider rather than toggling —
                            // the tap target is tiny, so a slider doesn't fit inline;
                            // this is the only spare gesture the icon has to offer it.
                            onLongClick = { showDimLevelDialog = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = dimScreenIcon(dimScreenEnabled),
                        contentDescription = dimLabel,
                        tint = if (dimScreenEnabled) colors.terra else colors.faint,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // ---- Routine progress chips: current sage-filled, completed faint,
        // upcoming dim. Only rendered when a routine is actively running. A
        // routine can have many steps (the Asma-ul-Husna preset has 99), so the
        // strip scrolls horizontally and keeps the current step in view. ----
        if (state.routineSteps.isNotEmpty()) {
            val chipListState = rememberLazyListState()
            LaunchedEffect(state.currentRoutineStepIndex) {
                val target = state.currentRoutineStepIndex
                if (target >= 0 && target < state.routineSteps.size) {
                    // Bias one item earlier so the current chip is not flush to
                    // the leading edge.
                    chipListState.animateScrollToItem((target - 1).coerceAtLeast(0))
                }
            }
            LazyRow(
                state = chipListState,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                itemsIndexed(
                    state.routineSteps,
                    key = { _, step -> step.stepOrder },
                ) { index, step ->
                    val isCurrent = index == state.currentRoutineStepIndex
                    val isCompleted = index < state.currentRoutineStepIndex
                    val isViewed = index == state.viewingRoutineStepIndex
                    val bg = if (isCurrent) colors.sage else colors.surface
                    val fg = when {
                        isCurrent -> colors.onSage
                        isCompleted -> colors.faint
                        else -> colors.dim
                    }
                    // Completed steps are tappable to glance back read-only
                    // (CounterViewModel.onSelectRoutineStep); tapping the
                    // currently-previewed chip again — or the live current
                    // chip while previewing — returns to the live step.
                    // Future (not-yet-reached) steps stay inert.
                    val chipClickable = isCompleted || (isCurrent && state.isViewingPastStep)
                    val chipAlpha = if (!isCurrent && !isCompleted) 0.55f else 1f
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(bg)
                            .then(
                                if (isViewed) {
                                    Modifier.border(1.5.dp, colors.terra, PillShape)
                                } else {
                                    Modifier
                                }
                            )
                            .alpha(chipAlpha)
                            .clickable(enabled = chipClickable) {
                                if (isCurrent || isViewed) {
                                    viewModel.onReturnToCurrentStep()
                                } else {
                                    viewModel.onSelectRoutineStep(index)
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(
                            (if (isCompleted) "✓ " else "") + "${step.tasbihName.substringBefore(' ')} ${step.targetCount}",
                            fontSize = 12.sp,
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ---- Tap area: the whole middle of the screen counts ----
        // The tap gesture and the scroll container sit on the outer Box, which
        // fills all space left between the top bar and the control row. Inside a
        // scroller the content is measured with an unbounded height, so
        // Arrangement.Center alone would not centre anything; BoxWithConstraints
        // hands us the real viewport height and `heightIn(min = ...)` pins the
        // content to it. Longer dhikr text grows past that and scrolls normally.
        val tapInteractionSource = remember { MutableInteractionSource() }
        val tapActionLabel = stringResource(R.string.counter_tap_action_label)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = tapInteractionSource,
                    indication = null,
                    // Room's seed data may not have loaded yet (finding #2) —
                    // disable the tap target rather than let it silently do
                    // nothing (or, pre-fix, crash the ViewModel). Also disabled
                    // while previewing a completed routine step (read-only).
                    enabled = state.sessionReady && !state.isViewingPastStep,
                    // TalkBack: "double-tap to Count" instead of the generic
                    // "double-tap to activate" on this full-screen tap zone.
                    onClickLabel = tapActionLabel,
                ) {
                    if (hapticMode == HapticMode.EVERY_TAP) {
                        haptics.tick()
                    }
                    viewModel.onTap()
                }
                .testTag(COUNTER_TAP_AREA_TEST_TAG),
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (scriptText.isNotEmpty()) {
                    val baseStyle = if (showArabic) ArabicLineStyle else pronunciationStyle
                    val scale = counterTextSize.scale
                    Text(
                        text = scriptText,
                        style = if (scale == 1f) baseStyle else baseStyle.copy(
                            fontSize = baseStyle.fontSize * scale,
                            lineHeight = baseStyle.lineHeight * scale,
                        ),
                        color = if (showArabic) colors.text else colors.dim,
                        textAlign = if (isLongText) TextAlign.Justify else TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isLongText) 16.dp else 22.dp),
                    )
                }

                // Progress ring + count
                Box(
                    modifier = Modifier
                        .size(ringSize)
                        .scale(countScale.value),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 12.dp.toPx()
                        val inset = strokeWidthPx / 2f
                        val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                        drawArc(
                            color = colors.track,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                        )
                        if (ringProgress > 0f) {
                            drawArc(
                                color = progressArcColor,
                                startAngle = -90f,
                                sweepAngle = 360f * ringProgress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.82f)
                            .clip(CircleShape)
                            .background(colors.card),
                    )
                    val countDescription = stringResource(
                        R.string.counter_count_description,
                        state.count,
                        state.dhikr.lapTarget,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Read as one polite live-region node ("437 of 1000")
                        // rather than the number and label as separate nodes.
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = countDescription
                        },
                    ) {
                        ClampedFontScale {
                            // During a lap-rollover flourish the number shows the
                            // lap target with the ring full, then snaps back.
                            // For a 1-per-lap dhikr the engine count is always 0,
                            // so between taps show reps done (lap - 1) instead.
                            // A past-step preview has no real lap/count from an
                            // engine at all — state.count is already the step's
                            // finished tally (CounterViewModel.onSelectRoutineStep),
                            // so show it as-is rather than run it through the
                            // lap-1 reasoning meant for a live session.
                            val shownCount = when {
                                state.isViewingPastStep -> state.count
                                showLapTargetNumber -> state.dhikr.lapTarget
                                state.dhikr.lapTarget == 1 -> (state.lap - 1).coerceAtLeast(0)
                                else -> state.count
                            }
                            // The ring/Box already pops on every tap (countScale,
                            // above); this adds a small slide+fade on the digits
                            // themselves so the new value reads as arriving, not
                            // just appearing — kept brief so it doesn't fight that
                            // bounce or lag behind fast repeated taps.
                            AnimatedContent(
                                targetState = shownCount,
                                transitionSpec = {
                                    if (reducedMotion || rapidTaps) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        (slideInVertically(tween(Motion.FAST_MS, easing = Motion.StandardEasing)) { it / 3 } +
                                            fadeIn(tween(Motion.FAST_MS)))
                                            .togetherWith(fadeOut(tween(Motion.FAST_MS)))
                                    }
                                },
                                label = "counterDigits",
                            ) { value ->
                                Text(
                                    text = value.localizedDigits(lang),
                                    style = countStyle,
                                    color = colors.text,
                                )
                            }
                        }
                        Text(
                            // A 1-per-lap dhikr counts whole laps, so its target
                            // is the lap count, not the (always 1) per-lap target.
                            text = stringResource(
                                R.string.counter_of_target,
                                if (state.dhikr.lapTarget == 1) state.totalLaps else state.dhikr.lapTarget,
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.faint,
                        )
                    }
                }

                // Lap pips + lap label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (i in 1..state.totalLaps) {
                            val pipWidth = if (!state.isViewingPastStep && i == state.lap) 26.dp else 8.dp
                            val pipColor = when {
                                // A past-step preview has no in-progress lap to
                                // highlight — the whole (single) lap is done, so
                                // paint it completed (sage) rather than
                                // "current" (terra).
                                state.isViewingPastStep -> colors.sage
                                i < state.lap -> colors.sage
                                i == state.lap -> colors.terra
                                else -> colors.track
                            }
                            Box(
                                modifier = Modifier
                                    .width(pipWidth)
                                    .height(8.dp)
                                    .clip(PillShape)
                                    .background(pipColor),
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.counter_lap_label,
                            state.lap,
                            state.totalLaps,
                            state.totalCount,
                            state.dhikr.lapTarget * state.totalLaps,
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.dim,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }

                Text(
                    text = when {
                        state.isViewingPastStep -> stringResource(R.string.counter_viewing_past_step_hint)
                        state.locked -> stringResource(R.string.counter_tap_hint_locked)
                        else -> stringResource(R.string.counter_tap_hint)
                    },
                    fontSize = 12.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        // ---- Control row: prev, undo, pause/resume, reset, next. (Notes moved
        // to the top bar, beside the lock toggle — it's a look-something-up
        // action, not a counting control, and living here made the row
        // asymmetric once the top bar had a free slot for it.)
        // Icon-only throughout (no visible labels) — every button's meaning is
        // still exposed to TalkBack via contentDescription. Outside a routine,
        // Prev/Next switch between tasbih in Tasbih-Library order. Inside a
        // routine, Prev glances back at the previous completed step
        // (read-only) and Next only works while previewing a completed step,
        // walking back toward the live one — future steps stay locked.
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            run {
                val previousLabel = stringResource(R.string.counter_previous_content_description)
                IconButtonTooltip(label = previousLabel) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable(enabled = state.canGoToPrevious) {
                                if (state.locked) showLockedHint() else viewModel.onPrevious()
                            }
                            .minTapTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = backChevronIcon(),
                            contentDescription = previousLabel,
                            tint = if (state.canGoToPrevious && !state.locked) colors.dim else colors.faint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            val undoLabel = stringResource(R.string.counter_undo)
            IconButtonTooltip(label = undoLabel) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        // Undo is blocked while locked, same as reset — a pocket
                        // touch shouldn't be able to unwind counted taps either.
                        .clickable(enabled = state.sessionReady && state.canUndo && !state.isViewingPastStep) {
                            if (state.locked) showLockedHint() else viewModel.onUndo()
                        }
                        .minTapTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = undoIcon(),
                        contentDescription = undoLabel,
                        tint = if (state.canUndo && !state.locked && !state.isViewingPastStep) colors.text else colors.faint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            val pauseResumeLabel = if (state.running) {
                stringResource(R.string.counter_pause)
            } else {
                stringResource(R.string.counter_resume)
            }
            IconButtonTooltip(label = pauseResumeLabel) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (state.locked) colors.surface else colors.sage)
                        // Room's seed data may not have loaded yet (finding #2).
                        // Pause/resume is blocked while locked too — the lock's
                        // whole point is a session that can't be knocked off
                        // course by a stray touch. Also blocked while
                        // previewing a completed routine step (read-only).
                        .clickable(enabled = state.sessionReady && !state.isViewingPastStep) {
                            if (state.locked) showLockedHint() else viewModel.onTogglePause()
                        }
                        .minTapTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.running) pauseIcon() else playIcon(),
                        contentDescription = pauseResumeLabel,
                        tint = if (state.locked || state.isViewingPastStep) colors.faint else colors.onSage,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            val resetLabel = stringResource(R.string.counter_reset_content_description)
            IconButtonTooltip(label = resetLabel) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        // Reset is destructive, so it is blocked while locked and, when
                        // unlocked, only opens the confirmation dialog. Also gated on
                        // sessionReady (finding #2) — Room's seed data may not have
                        // loaded yet — and blocked while previewing a completed
                        // routine step (read-only).
                        .clickable(enabled = state.sessionReady && !state.isViewingPastStep) {
                            if (state.locked) {
                                showLockedHint()
                            } else {
                                val restore = viewModel.onReset()
                                if (restore != null) {
                                    snackbar?.show(resetDoneText, undoText) { restore() }
                                }
                            }
                        }
                        .minTapTarget(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resetIcon(),
                        contentDescription = resetLabel,
                        tint = if (state.locked || state.isViewingPastStep) colors.faint else colors.dim,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            run {
                val nextLabel = stringResource(R.string.counter_next_content_description)
                IconButtonTooltip(label = nextLabel) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable(enabled = state.canGoToNext) {
                                if (state.locked) showLockedHint() else viewModel.onNext()
                            }
                            .minTapTarget(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = forwardChevronIcon(),
                            contentDescription = nextLabel,
                            tint = if (state.canGoToNext && !state.locked) colors.dim else colors.faint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    if (state.isRoutineComplete) {
        AlertDialog(
            onDismissRequest = { /* no-op: dismissal happens via the Done button only */ },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = RoundedCornerShape(34.dp),
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(colors.sageSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = checkIcon(),
                            contentDescription = stringResource(R.string.routine_complete_check_content_description),
                            tint = colors.sage,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.routine_complete_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.routine_complete_body,
                            state.routineName ?: state.dhikr.displayName(lang),
                            state.totalCount,
                            formatDuration(elapsedSeconds),
                        ),
                        fontSize = 13.5.sp,
                        color = colors.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        // "Again" replays the routine from step 1 in place — no
                        // navigation, the dialog just closes and counting resumes.
                        TextButton(onClick = { viewModel.onRoutineRestart() }) {
                            Text(
                                text = stringResource(R.string.routine_complete_again),
                                color = colors.dim,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // "Done" clears the flag and pops back (Home when the
                        // counter was opened from a notification, otherwise the
                        // originating screen).
                        TextButton(
                            onClick = {
                                viewModel.onRoutineCompleteAcknowledged()
                                onBack()
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.routine_complete_done),
                                color = colors.sage,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showSessionSummary) {
        SessionSummaryDialog(
            startedAtMillis = state.sessionStartedAtMillis,
            elapsedSeconds = elapsedSeconds,
            totalCount = state.totalCount,
            onDismiss = { showSessionSummary = false },
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text(stringResource(R.string.counter_notes_dialog_title)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            text = {
                Text(
                    text = state.dhikr.displayNote(lang).ifBlank { stringResource(R.string.counter_notes_empty) },
                    fontSize = 14.sp,
                    color = colors.dim,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text(
                        text = stringResource(R.string.session_summary_close),
                        color = colors.sage,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }

    if (showDimLevelDialog) {
        // Turn dim on for the dialog's duration so the slider actually
        // previews something — adjusting a level nobody can see defeats the
        // point. Only auto-enables; never auto-disables on dismiss, since the
        // user may have opened this while dim was already on.
        LaunchedEffect(Unit) {
            if (!dimScreenEnabled) onToggleDimScreen()
        }
        DimLevelDialog(
            level = dimBrightnessLevel,
            onLevelChange = onDimBrightnessLevelChange,
            onDismiss = { showDimLevelDialog = false },
        )
    }
}

/**
 * Dim-level slider, opened by long-pressing the top-bar dim button. The
 * slider's range is anchored to the device's own brightness: its top is
 * whatever system brightness is currently set (dimming is never brighter
 * than "normal"), its bottom is the practical minimum the screen can show.
 * Both bounds are read once, at dialog-open — the system setting itself
 * never moves while this is open, since the window-level override this
 * screen applies doesn't touch Settings.System.SCREEN_BRIGHTNESS.
 */
@Composable
private fun DimLevelDialog(
    level: Float,
    onLevelChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val context = LocalContext.current
    val maxBrightness = remember { currentSystemBrightnessFraction(context) }
    // A level saved under a brighter device setting can exceed today's max
    // (e.g. the user dimmed the device itself since last time) — clamp on
    // open rather than let the slider show a value past its own track end.
    // Pushed back through onLevelChange immediately so the persisted value
    // matches what's shown even if the user closes without touching the
    // slider — otherwise the next-applied brightness would silently differ
    // from the dialog's own display.
    val clampedLevel = level.coerceIn(MIN_SCREEN_BRIGHTNESS, maxBrightness)
    var sliderValue by remember(level) { mutableStateOf(clampedLevel) }
    LaunchedEffect(clampedLevel) {
        if (clampedLevel != level) onLevelChange(clampedLevel)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.counter_dim_level_dialog_title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        shape = DialogShape,
        text = {
            Column {
                Text(
                    text = stringResource(R.string.counter_dim_level_percent, (sliderValue * 100).toInt()),
                    fontSize = 13.sp,
                    color = colors.faint,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onLevelChange(it)
                    },
                    valueRange = MIN_SCREEN_BRIGHTNESS..maxBrightness,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.sage,
                        activeTrackColor = colors.sage,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_summary_close),
                    color = colors.sage,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

/**
 * Session-summary dialog opened by tapping the top-bar elapsed-time label.
 * Pure display of state already tracked by CounterViewModel — started-at
 * clock time, duration, total counts, and pace — no new persistence.
 */
@Composable
private fun SessionSummaryDialog(
    startedAtMillis: Long,
    elapsedSeconds: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val startedAtLabel = remember(startedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startedAtMillis))
    }
    val rateText = if (elapsedSeconds > 4) {
        val rate = (totalCount.toFloat() / (elapsedSeconds / 60f)).toInt()
        if (rate > 0) stringResource(R.string.session_summary_rate, rate) else null
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_summary_title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        shape = DialogShape,
        text = {
            Column {
                SessionSummaryLine(stringResource(R.string.session_summary_started, startedAtLabel))
                SessionSummaryLine(stringResource(R.string.session_summary_duration, formatDuration(elapsedSeconds)))
                SessionSummaryLine(stringResource(R.string.session_summary_counts, totalCount))
                SessionSummaryLine(rateText ?: stringResource(R.string.session_summary_rate_unavailable))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_summary_close),
                    color = colors.sage,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

/**
 * Wraps an icon-only button so a long-press pops up its name as a small
 * tooltip label — every button on this screen is icon-only (no visible
 * text), so this is the only way to surface what each one does short of
 * TalkBack. [label] doubles as the tooltip text and should be the same
 * string already passed as the wrapped button's contentDescription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconButtonTooltip(
    label: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun SessionSummaryLine(text: String) {
    val colors = DhikrTheme.colors
    Text(
        text = text,
        fontSize = 14.sp,
        color = colors.dim,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Plain "MM:SS" duration, with no pace readout — use for dialog body text
 * where a bare duration is needed (see formatSessionLabel below for the
 * pace-augmented variant used in the top-bar session label). */
private fun formatDuration(elapsedSeconds: Int): String {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** "12:34" once the session is a few seconds old, plus a "· 41/min" pace readout. */
private fun formatSessionLabel(elapsedSeconds: Int, total: Int): String {
    val time = formatDuration(elapsedSeconds)
    if (elapsedSeconds <= 4) return time
    val rate = (total.toFloat() / (elapsedSeconds / 60f)).toInt()
    return if (rate > 0) "$time  ·  $rate/min" else time
}
