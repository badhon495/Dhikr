package com.dhikr.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.datastore.ThemeMode
import com.dhikr.app.core.localization.AppLanguage
import com.dhikr.app.ui.NavCountIcon
import com.dhikr.app.ui.NavInsightsIcon
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.Caprasimo
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.PillShape
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector?,
    val title: String,
    val body: String,
)

/**
 * Shown once, before the user reaches Home (plan.md §25) — a full-screen
 * overlay rendered on top of DhikrApp's Scaffold rather than a NavHost
 * destination, so there is no back-stack entry or startDestination gating to
 * manage. [onFinished] is called from Skip or the last page's "Get started"
 * button; the caller is responsible for persisting the "seen" flag.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val colors = DhikrTheme.colors
    // Page 0 is the language + theme setup; its title/body come from the list,
    // the selectors are rendered specially in the pager below.
    val pages = listOf(
        OnboardingPage(null, stringResource(R.string.onboarding_setup_title), stringResource(R.string.onboarding_setup_body)),
        OnboardingPage(null, stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body)),
        OnboardingPage(NavCountIcon, stringResource(R.string.onboarding_tap_title), stringResource(R.string.onboarding_tap_body)),
        OnboardingPage(OnboardingLapsIcon, stringResource(R.string.onboarding_laps_title), stringResource(R.string.onboarding_laps_body)),
        OnboardingPage(NavInsightsIcon, stringResource(R.string.onboarding_history_title), stringResource(R.string.onboarding_history_body)),
        OnboardingPage(OnboardingPrivacyIcon, stringResource(R.string.onboarding_privacy_title), stringResource(R.string.onboarding_privacy_body)),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage by remember { derivedStateOf { pagerState.currentPage == pages.lastIndex } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            // Skip is always available — plan.md §25 requires onboarding to be
            // skippable at any point, not just from the last page.
            TextButton(onClick = onFinished, modifier = Modifier.testTag("onboarding_skip")) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = colors.dim,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            if (page == 0) {
                SetupPageContent(
                    page = pages[0],
                    language = language,
                    onLanguageChange = onLanguageChange,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                )
            } else {
                OnboardingPageContent(pages[page])
            }
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(PillShape)
                .background(colors.sage)
                .clickable(role = Role.Button) {
                    if (isLastPage) {
                        onFinished()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
                .minTapTarget()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next,
                ),
                color = colors.onSage,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SetupPageContent(
    page: OnboardingPage,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = page.title,
            fontFamily = Caprasimo,
            fontSize = 28.sp,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = page.body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        SetupGroup(
            label = stringResource(R.string.onboarding_setup_language),
            options = listOf(
                AppLanguage.ENGLISH to stringResource(R.string.settings_language_english),
                AppLanguage.BANGLA to stringResource(R.string.settings_language_bangla),
            ),
            selected = language,
            onSelect = onLanguageChange,
        )
        Spacer(modifier = Modifier.height(18.dp))
        SetupGroup(
            label = stringResource(R.string.onboarding_setup_theme),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
            ),
            selected = themeMode,
            onSelect = onThemeChange,
        )
    }
}

@Composable
private fun <T> SetupGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = DhikrTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = colors.faint, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (active) colors.sage else colors.card)
                        .clickable(role = Role.Button) { onSelect(value) }
                        .minTapTarget()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) colors.onSage else colors.dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val colors = DhikrTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (page.icon != null) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.sageSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null, // decorative — the title/body carry the meaning
                    tint = colors.sage,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
        Text(
            text = page.title,
            fontFamily = Caprasimo,
            fontSize = 28.sp,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = page.body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.dim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val colors = DhikrTheme.colors
    val description = stringResource(R.string.onboarding_page_indicator, currentPage + 1, pageCount)
    Row(
        modifier = modifier.semantics {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (active) 20.dp else 7.dp, 7.dp)
                    .clip(PillShape)
                    .background(if (active) colors.sage else colors.line),
            )
        }
    }
}
