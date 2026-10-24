package com.dhikr.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.database.TasbihHistoryGroup
import com.dhikr.app.core.share.ProgressShareText
import com.dhikr.app.core.utilities.Consistency
import com.dhikr.app.core.localization.LocalAppLanguage
import com.dhikr.app.core.localization.localizedDigits
import com.dhikr.app.ui.INSIGHTS_SCREEN_TEST_TAG
import com.dhikr.app.ui.CheckIcon
import com.dhikr.app.ui.ScheduleIcon
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DHIKR_HISTORY_PREVIEW_LIMIT = 3

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onStartCounting: () -> Unit,
    onSeeAllMonths: () -> Unit,
    onSeeAllDhikr: () -> Unit,
    scrollToTopSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = DhikrTheme.colors
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) scrollState.animateScrollTo(0)
    }

    if (state.isEmpty) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ScheduleIcon,
                    contentDescription = stringResource(R.string.insights_empty_icon_content_description),
                    tint = colors.dim,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                stringResource(R.string.insights_empty_title),
                fontSize = 18.sp,
                color = colors.text,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.insights_empty_body),
                fontSize = 13.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(colors.sage)
                    .clickable(role = Role.Button) { onStartCounting() }
                    .minTapTarget()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.insights_empty_cta), color = colors.onSage)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(scrollState)
            .testTag(INSIGHTS_SCREEN_TEST_TAG)
            .padding(16.dp),
    ) {
        val monthName = remember {
            SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().time)
        }
        val context = LocalContext.current
        val shareTitle = stringResource(R.string.insights_share_title)
        val shareTotalFormat = stringResource(R.string.insights_share_total)
        val shareChooser = stringResource(R.string.insights_share_chooser)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.insights_title),
                    fontSize = 23.sp,
                    color = colors.text,
                    modifier = Modifier.headingSemantics(),
                )
                Text(monthName, fontSize = 13.sp, color = colors.dim, modifier = Modifier.padding(top = 2.dp))
            }
            // Share today's summary (plan.md §42). Only shown once there is
            // something counted today to share.
            if (state.today > 0) {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(colors.surface)
                        .clickable(role = Role.Button) {
                            viewModel.loadTodayForShare { items ->
                                if (items.isEmpty()) return@loadTodayForShare
                                val text = ProgressShareText.build(shareTitle, items) {
                                    shareTotalFormat.format(it)
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, shareChooser))
                            }
                        }
                        .minTapTarget()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.insights_share_today), fontSize = 12.5.sp, color = colors.text)
                }
            }
        }

        // Totals 2x2 grid
        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_today), state.today, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_this_week), state.week, Modifier.weight(1f))
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalTile(stringResource(R.string.insights_this_month), state.month, Modifier.weight(1f))
            TotalTile(stringResource(R.string.insights_all_time), state.allTime, Modifier.weight(1f))
        }

        if (state.weeklyGoalTarget > 0) {
            WeeklyGoalBar(state.week, state.weeklyGoalTarget)
        }

        // Last 7 days
        Text(
            stringResource(R.string.insights_last_7_days),
            fontSize = 11.5.sp,
            color = colors.dim,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 8.dp)
                .headingSemantics(),
        )
        // Labels built here (not in the repository) so they follow the in-app
        // language and re-render when it changes.
        val appLanguage = LocalAppLanguage.current
        val dayLabels = remember(appLanguage, state.last7Days.size) {
            val format = SimpleDateFormat("EEE", Locale.forLanguageTag(appLanguage.tag))
            val cal = Calendar.getInstance()
            val count = state.last7Days.size
            List(count) { index ->
                cal.timeInMillis = System.currentTimeMillis()
                cal.add(Calendar.DAY_OF_YEAR, index - (count - 1))
                format.format(cal.time)
            }
        }
        val barChartSummary = remember(state.last7Days, dayLabels) {
            state.last7Days.mapIndexed { i, (_, value) -> "${dayLabels[i]} $value" }.joinToString(", ")
        }
        val barChartDescription = stringResource(R.string.insights_bar_chart_description, barChartSummary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp)
                .clearAndSetSemantics { contentDescription = barChartDescription },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val maxValue = remember(state.last7Days) {
                (state.last7Days.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            }
            state.last7Days.forEachIndexed { index, (_, value) ->
                val isToday = index == state.last7Days.lastIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text(
                        value.localizedDigits(appLanguage),
                        fontSize = 10.5.sp,
                        color = colors.dim,
                        maxLines = 1,
                        softWrap = false,
                    )
                    // Bar area takes the leftover height, so a tall bar can
                    // never push the day label out of the card.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((maxHeight * (value.toFloat() / maxValue)).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isToday) colors.terra else colors.sage),
                        )
                    }
                    Text(
                        dayLabels[index],
                        fontSize = 10.5.sp,
                        color = colors.faint,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // Consistency calendar
        Text(
            stringResource(R.string.insights_consistency),
            fontSize = 11.5.sp,
            color = colors.dim,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 2.dp)
                .headingSemantics(),
        )
        state.consistency?.let { ConsistencyCard(it) }
        val activeDaysThisMonth = state.calendarIntensity.values.count { it > 0 }
        Text(
            stringResource(R.string.insights_consistency_meta, activeDaysThisMonth),
            fontSize = 10.5.sp,
            color = colors.faint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(12.dp),
        ) {
            val sortedEntries = remember(state.calendarIntensity) {
                state.calendarIntensity.entries.sortedBy { it.key }
            }
            sortedEntries.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { (day, count) ->
                        val intensity = when {
                            count == 0 -> colors.track
                            count < 33 -> colors.sageSoft
                            count < 100 -> colors.sageMid
                            else -> colors.sage
                        }
                        val goalMet = count > 0 && count >= state.dailyGoalTarget
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(intensity),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.localizedDigits(LocalAppLanguage.current), fontSize = 12.sp, color = colors.text)
                            if (goalMet) {
                                Icon(
                                    imageVector = CheckIcon,
                                    contentDescription = stringResource(R.string.insights_goal_met),
                                    tint = colors.terra,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(10.dp),
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.insights_legend_less), fontSize = 10.sp, color = colors.faint)
                Row(modifier = Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(colors.track, colors.sageSoft, colors.sageMid, colors.sage).forEach { c ->
                        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
                Text(stringResource(R.string.insights_legend_more), fontSize = 10.sp, color = colors.faint)
            }
        }

        // Previous month summary + link to the full month-by-month history
        val previousMonth = state.previousMonth
        if (previousMonth != null && previousMonth.total > 0) {
            val prevMonthName = remember(previousMonth.monthStartMillis) {
                SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(previousMonth.monthStartMillis))
            }
            Text(
                stringResource(R.string.insights_last_month),
                fontSize = 11.5.sp,
                color = colors.dim,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 8.dp)
                    .headingSemantics(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.card)
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(prevMonthName, fontSize = 14.5.sp, color = colors.text)
                    Text(
                        previousMonth.total.localizedDigits(LocalAppLanguage.current),
                        fontSize = 20.sp,
                        fontFamily = Caprasimo,
                        color = colors.terra,
                    )
                }
                Text(
                    stringResource(
                        R.string.insights_month_active_days,
                        previousMonth.consistentDays,
                        previousMonth.daysInMonth,
                    ),
                    fontSize = 11.sp,
                    color = colors.faint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(PillShape)
                .clickable(role = Role.Button) { onSeeAllMonths() }
                .minTapTarget()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.insights_see_all_months),
                fontSize = 12.sp,
                color = colors.sage,
            )
        }

        // History grouped by Dhikr
        val todayLabel = stringResource(R.string.insights_day_today)
        val yesterdayLabel = stringResource(R.string.insights_day_yesterday)
        Row(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.insights_history_title),
                fontSize = 11.5.sp,
                color = colors.dim,
                modifier = Modifier.headingSemantics(),
            )
            Text(
                stringResource(R.string.insights_history_grouped),
                fontSize = 10.5.sp,
                color = colors.faint,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        state.historyByTasbih.take(DHIKR_HISTORY_PREVIEW_LIMIT).forEach { group ->
            DhikrHistoryCard(group, todayLabel, yesterdayLabel)
        }
        if (state.historyByTasbih.size > DHIKR_HISTORY_PREVIEW_LIMIT) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(PillShape)
                    .clickable(role = Role.Button) { onSeeAllDhikr() }
                    .minTapTarget()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.insights_see_all_dhikr),
                    fontSize = 12.sp,
                    color = colors.sage,
                )
            }
        }
    }
}

@Composable
fun DhikrHistoryCard(
    group: TasbihHistoryGroup,
    todayLabel: String,
    yesterdayLabel: String,
    modifier: Modifier = Modifier,
    showStats: Boolean = false,
) {
    val colors = DhikrTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(group.tasbihName, fontSize = 14.5.sp, color = colors.text)
            Text(group.lifetimeTotal.localizedDigits(LocalAppLanguage.current), fontSize = 14.5.sp, color = colors.terra)
        }
        if (showStats) {
            val speed = group.speedPerMin
            val parts = buildList {
                add(stringResource(R.string.insights_dhikr_stat_week, group.weekTotal))
                add(stringResource(R.string.insights_dhikr_stat_month, group.monthTotal))
                if (speed != null) {
                    add(stringResource(R.string.insights_dhikr_stat_speed, Math.round(speed).toInt()))
                }
            }
            Text(
                parts.joinToString("  ·  "),
                fontSize = 11.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        group.dailyTotals.forEach { (dayStartMillis, count) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(
                    formatDayLabel(dayStartMillis, todayLabel, yesterdayLabel),
                    fontSize = 11.sp,
                    color = colors.faint,
                    modifier = Modifier.width(72.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(PillShape)
                        .background(colors.track),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((count.toFloat() / 200f).coerceIn(0f, 1f))
                            .height(7.dp)
                            .clip(PillShape)
                            .background(colors.sage),
                    )
                }
                Text(count.localizedDigits(LocalAppLanguage.current), fontSize = 12.sp, color = colors.dim, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// Built once per process rather than per row: formatDayLabel runs inside a
// per-day loop over a LazyColumn-rendered breakdown, and SimpleDateFormat's
// own parse-pattern setup showed up as a scroll-jank contributor there.
private val dayLabelFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())

private fun formatDayLabel(dayStartMillis: Long, todayLabel: String, yesterdayLabel: String): String {
    val target = Calendar.getInstance().apply { timeInMillis = dayStartMillis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(target, today) -> todayLabel
        isSameDay(target, yesterday) -> yesterdayLabel
        else -> dayLabelFormat.format(Date(dayStartMillis))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** Rolling seven-day progress toward the optional weekly goal. */
@Composable
private fun WeeklyGoalBar(week: Int, target: Int) {
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current
    val fraction = (week.toFloat() / target).coerceIn(0f, 1f)
    val description = stringResource(R.string.insights_weekly_goal_description, week, target)
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .clearAndSetSemantics { contentDescription = description }
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.insights_weekly_goal), fontSize = 12.5.sp, color = colors.dim)
            Text(
                "${week.localizedDigits(lang)} / ${target.localizedDigits(lang)}",
                fontSize = 12.5.sp,
                color = if (week >= target) colors.sage else colors.text,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(PillShape)
                .background(colors.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(PillShape)
                    .background(colors.sage),
            )
        }
    }
}

/**
 * Consistency run and milestone (plan.md §18). Positive framing only: it
 * names what has been kept up, never what was missed.
 */
@Composable
private fun ConsistencyCard(summary: Consistency.Summary) {
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.sageSoft)
            .padding(14.dp),
    ) {
        Text(
            if (summary.currentRun > 0) {
                pluralStringResource(R.plurals.insights_consistency_run, summary.currentRun, summary.currentRun.localizedDigits(lang))
            } else {
                stringResource(R.string.insights_consistency_start)
            },
            fontSize = 15.sp,
            color = colors.text,
        )
        val reached = summary.reachedMilestone
        val next = summary.nextMilestone
        val detail = when {
            reached != null && reached == summary.currentRun ->
                pluralStringResource(R.plurals.insights_consistency_milestone, reached, reached.localizedDigits(lang))
            next != null && summary.currentRun > 0 ->
                pluralStringResource(R.plurals.insights_consistency_next, next, next.localizedDigits(lang))
            else -> null
        }
        if (detail != null) {
            Text(detail, fontSize = 12.sp, color = colors.dim, modifier = Modifier.padding(top = 3.dp))
        }
        if (summary.bestRun > summary.currentRun) {
            Text(
                pluralStringResource(R.plurals.insights_consistency_best, summary.bestRun, summary.bestRun.localizedDigits(lang)),
                fontSize = 11.5.sp,
                color = colors.faint,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun TotalTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = DhikrTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Text(label.uppercase(), fontSize = 10.5.sp, color = colors.dim)
        Text(
            value.localizedDigits(LocalAppLanguage.current),
            fontSize = 24.sp,
            fontFamily = Caprasimo,
            color = colors.text,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
