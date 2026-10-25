package com.dhikr.app.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.dao.RoutineWithSteps
import com.dhikr.app.core.localization.LocalAppLanguage
import com.dhikr.app.core.localization.displayName
import com.dhikr.app.core.localization.displayPronunciation
import com.dhikr.app.core.localization.localizedDigits
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.core.utilities.IslamicSeason
import java.text.DateFormat
import java.util.Date
import com.dhikr.app.ui.BookIcon
import com.dhikr.app.ui.HOME_SCREEN_TEST_TAG
import com.dhikr.app.ui.LocalReducedMotion
import com.dhikr.app.ui.Motion
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import kotlin.math.min

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContinueSession: () -> Unit,
    onOpenTasbih: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenDua: () -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scrollState.animateScrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(scrollState)
            .testTag(HOME_SCREEN_TEST_TAG)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Greeting + goal ring
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            // weight(1f): on a narrow phone the greeting's intrinsic width
            // otherwise eats the row and squeezes the fixed-size ring into an
            // oval; with it the text wraps and the ring keeps its 86dp.
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.home_greeting),
                    fontFamily = Caprasimo,
                    fontSize = 29.sp,
                    color = colors.text,
                )
                Text(
                    state.dateLabel,
                    fontSize = 13.5.sp,
                    color = colors.dim,
                    modifier = Modifier.padding(top = 3.dp),
                )
                state.hijriDate?.let { hijri ->
                    val months = stringArrayResource(R.array.hijri_months)
                    Text(
                        stringResource(
                            R.string.home_hijri_date,
                            hijri.day.localizedDigits(lang),
                            months.getOrElse(hijri.month - 1) { "" },
                            hijri.year.localizedDigits(lang),
                        ),
                        fontSize = 12.5.sp,
                        color = colors.faint,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                CurrentWaqtLine(waqt = state.currentWaqt)
            }
            GoalRing(
                progress = if (state.dailyGoalTarget > 0) state.todayTotal.toFloat() / state.dailyGoalTarget else 0f,
                contentDescription = stringResource(
                    R.string.home_goal_ring_description,
                    state.todayTotal,
                    state.dailyGoalTarget,
                ),
            )
        }

        state.season?.let { season ->
            SeasonBanner(
                season = season,
                onOpen = state.seasonTasbihId?.let { id -> { onOpenTasbih(id) } },
            )
        }

        // Continue session
        state.continueSession?.let { info ->
            val continueDescription = stringResource(
                R.string.home_continue_session_description,
                info.tasbihName,
                info.count,
                info.target,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.sageSoft)
                    .border(1.dp, colors.line, RoundedCornerShape(30.dp))
                    .clickable { onContinueSession() }
                    .semantics(mergeDescendants = true) { contentDescription = continueDescription }
                    .padding(20.dp),
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.sage),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.onSage,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        stringResource(R.string.home_continue_session_kicker),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.dim,
                    )
                    Text(
                        info.tasbihName,
                        fontSize = 17.sp,
                        color = colors.text,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    "${info.count.localizedDigits(lang)}/${info.target.localizedDigits(lang)}",
                    fontSize = 14.5.sp,
                    color = colors.dim,
                )
            }
        }

        // Routines — the favorited ones. Full-width cards, one per row: matches
        // the Favourites list width but reads heavier — a step-count chip leads
        // each card and the first step names preview the plan, so the two
        // sections stay visually distinct. Empty = a hint pointing at Routines.
        Column {
            SectionHeader(
                title = stringResource(R.string.home_routines_title),
                actionLabel = stringResource(R.string.home_routines_manage),
                onAction = onOpenRoutines,
            )
            if (state.routines.isEmpty()) {
                Text(
                    stringResource(R.string.home_routines_empty_hint),
                    fontSize = 13.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            state.routines.forEach { routineWithSteps ->
                val done = routineWithSteps.routine.id in state.completedRoutineIds
                val fraction = if (done) 0f
                    else (state.routineProgress[routineWithSteps.routine.id] ?: 0f).coerceIn(0f, 1f)
                RoutineHomeCard(
                    routine = routineWithSteps,
                    tasbihNamesById = state.tasbihNamesById,
                    completedToday = done,
                    fraction = fraction,
                    onStart = { onStartRoutine(routineWithSteps.routine.id) },
                )
            }
        }

        // Favourites
        Column {
            SectionHeader(
                title = stringResource(R.string.home_favorites_title),
                actionLabel = stringResource(R.string.home_favorites_all),
                onAction = onOpenLibrary,
            )
            if (state.favorites.isEmpty()) {
                Text(
                    stringResource(R.string.home_favorites_empty_hint),
                    fontSize = 13.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            state.favorites.forEach { tasbih ->
                val fill = (state.tasbihProgress[tasbih.id] ?: 0f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.card)
                        .clickable { onOpenTasbih(tasbih.id) },
                ) {
                    // Green fill growing left-to-right with today's counting
                    // progress toward this Tasbih's total goal.
                    if (fill > 0f) {
                        Box(modifier = Modifier.matchParentSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fill)
                                    .background(colors.sageSoft),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tasbih.displayName(lang),
                                fontSize = 14.5.sp,
                                color = colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                tasbih.displayPronunciation(lang),
                                fontSize = 12.sp,
                                color = colors.faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Capped so a long Arabic text can't squeeze the name
                        // column down to a single character per line.
                        Text(
                            tasbih.arabic,
                            fontSize = 14.sp,
                            color = colors.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .widthIn(max = 120.dp),
                        )
                    }
                }
            }
        }

        // Dua — its own section, set apart from Favourites above: a sage icon
        // badge (same accent used by the counter/add affordances elsewhere)
        // and a trailing chevron mark this as a link into a whole separate
        // screen, not another countable favorite card.
        Column {
            Text(
                stringResource(R.string.home_dua_card_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                color = colors.text,
                modifier = Modifier.headingSemantics(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .clickable(role = Role.Button) { onOpenDua() }
                    .padding(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.sageSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BookIcon,
                        contentDescription = null,
                        tint = colors.sage,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    stringResource(R.string.home_dua_card_subtitle),
                    fontSize = 13.sp,
                    color = colors.dim,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Current waqt + next prayer time, one line under the Hijri date. Hidden
 *  when no location is set (Settings → Prayer times). */
@Composable
private fun CurrentWaqtLine(waqt: CurrentWaqtInfo?) {
    if (waqt == null) return
    val colors = DhikrTheme.colors
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val prayerName = stringResource(ReminderNotifications.prayerNameRes(waqt.prayer))
    val next = waqt.nextStartMillis?.let { next ->
        val prayers = com.dhikr.app.core.prayer.Prayer.entries
        val nextPrayer = prayers[(waqt.prayer.ordinal + 1) % prayers.size]
        val nextName = stringResource(ReminderNotifications.prayerNameRes(nextPrayer))
        stringResource(R.string.home_current_waqt_next, nextName, timeFormat.format(Date(next)))
    }
    Text(
        stringResource(R.string.home_current_waqt_kicker, prayerName) +
            (next?.let { " · $it" } ?: ""),
        fontSize = 12.5.sp,
        color = colors.sage,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp),
    )
}

/**
 * A gentle note for special times (see [IslamicSeason]) —
 * with a shortcut to the fitting dhikr when there is one.
 */
@Composable
private fun SeasonBanner(season: IslamicSeason, onOpen: (() -> Unit)?) {
    val colors = DhikrTheme.colors
    val (title, body) = when (season) {
        IslamicSeason.LAST_TEN_NIGHTS -> R.string.home_season_last_ten_title to R.string.home_season_last_ten_body
        IslamicSeason.RAMADAN -> R.string.home_season_ramadan_title to R.string.home_season_ramadan_body
        IslamicSeason.ARAFAH -> R.string.home_season_arafah_title to R.string.home_season_arafah_body
        IslamicSeason.DHUL_HIJJAH -> R.string.home_season_dhul_hijjah_title to R.string.home_season_dhul_hijjah_body
        IslamicSeason.ASHURA -> R.string.home_season_ashura_title to R.string.home_season_ashura_body
        IslamicSeason.FRIDAY -> R.string.home_season_friday_title to R.string.home_season_friday_body
        IslamicSeason.WHITE_DAYS -> R.string.home_season_white_days_title to R.string.home_season_white_days_body
        IslamicSeason.FAST_TOMORROW -> R.string.home_season_fast_tomorrow_title to R.string.home_season_fast_tomorrow_body
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button) { onOpen() } else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
    ) {
        Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
        Text(stringResource(body), fontSize = 12.5.sp, color = colors.dim, modifier = Modifier.padding(top = 3.dp))
        if (onOpen != null) {
            Text(
                stringResource(R.string.home_season_action),
                fontSize = 12.5.sp,
                color = colors.sage,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * A single routine on Home: a full-width card led by a step-count chip, with
 * the first step names previewing the plan and the routine's total on the
 * right. Tap starts the routine. Matches the Favourites list width but is
 * deliberately busier so the two sections don't blur together.
 */
@Composable
private fun RoutineHomeCard(
    routine: RoutineWithSteps,
    tasbihNamesById: Map<String, String>,
    completedToday: Boolean,
    fraction: Float,
    onStart: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current
    val steps = routine.steps.sortedBy { it.stepOrder }
    val totalCount = steps.sumOf { it.targetCount }
    val previewNames = steps.take(3).joinToString(" · ") { tasbihNamesById[it.tasbihId] ?: it.tasbihId }
    val moreCount = steps.size - 3

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (completedToday) colors.sageSoft else colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button) { onStart() },
    ) {
        // Green fill growing left-to-right with today's progress through the
        // routine; a completed routine uses the solid tint above instead.
        if (fraction > 0f) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(colors.sageSoft),
                )
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.home_routine_step_chip, steps.size, totalCount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = colors.onSage,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.sage)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Text(
                    routine.routine.displayName(lang),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
            }
            if (previewNames.isNotEmpty()) {
                Text(
                    if (moreCount > 0) {
                        stringResource(R.string.home_routine_step_preview_more, previewNames, moreCount)
                    } else {
                        previewNames
                    },
                    fontSize = 12.sp,
                    color = colors.dim,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Section label + trailing action link, shared by the Home sections. */
@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = DhikrTheme.colors
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = colors.text,
            modifier = Modifier.headingSemantics(),
        )
        Text(
            actionLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.terra,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(role = Role.Button) { onAction() }
                .minTapTarget()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GoalRing(progress: Float, contentDescription: String) {
    val colors = DhikrTheme.colors
    val reducedMotion = LocalReducedMotion.current
    // Sweep the arc up to a changed daily total instead of snapping, on the same
    // curve the counter ring uses.
    val animatedProgress by animateFloatAsState(
        targetValue = min(1f, progress),
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(Motion.STANDARD_MS, easing = Motion.StandardEasing)
        },
        label = "goal-ring",
    )
    Box(
        modifier = Modifier
            .size(86.dp)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = colors.track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = colors.terra,
                startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            "${(animatedProgress * 100).toInt().localizedDigits(LocalAppLanguage.current)}%",
            fontSize = 17.sp,
            color = colors.text,
        )
    }
}
