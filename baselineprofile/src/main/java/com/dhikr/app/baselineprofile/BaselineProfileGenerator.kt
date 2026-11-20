package com.dhikr.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.badhon495.dhikr"
private const val TIMEOUT = 5_000L

/**
 * Baseline Profile generator. Plays the critical user journeys from plan.md §46 on-device so
 * AGP can capture a baseline profile (and, with [includeInStartupProfile], a startup profile).
 *
 * Not run in this task — only compiled. It executes on a connected device in Task 5 via
 * `./gradlew :app:generateBaselineProfile`.
 *
 * Assumes an English device locale: nav-tab labels (`Home`, `Tasbih`, `Insights`) resolve from
 * `res/values/strings.xml`; on a Bengali device these strings differ and the text selectors
 * would not match.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Home rendered.
        device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)

        // Home -> Tasbih tab.
        clickTab("Tasbih")
        device.wait(Until.hasObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT)

        // Open the first Tasbih into the Counter. The list is a LazyColumn whose rows may be
        // wrapped, so `children` can be empty — fall back to the first built-in seed name.
        val list = device.wait(
            Until.findObject(By.res(PACKAGE, "tasbih_list")), TIMEOUT,
        )
        val firstRow = list?.children?.firstOrNull()
        if (firstRow != null) {
            firstRow.click()
        } else {
            device.findObject(By.text("SubhanAllah"))?.click()
        }
        val tapArea = device.wait(
            Until.findObject(By.res(PACKAGE, "counter_tap_area")), TIMEOUT,
        )

        // Count 50 taps.
        repeat(50) {
            tapArea?.click()
            device.waitForIdle()
        }

        // Undo once.
        device.findObject(By.textContains("Undo"))?.click()
        device.waitForIdle()

        // Insights tab, then the monthly-history link ("See all months").
        clickTab("Insights")
        device.wait(Until.hasObject(By.res(PACKAGE, "insights_screen")), TIMEOUT)
        device.findObject(By.textContains("months"))?.click()
        device.waitForIdle()

        // Back to Home.
        device.pressBack()
        clickTab("Home")
        device.wait(Until.hasObject(By.res(PACKAGE, "home_screen")), TIMEOUT)
    }

    /**
     * Unselected bottom-nav tabs show only an icon — the label lives in the
     * icon's contentDescription and only appears as visible text once the tab
     * is selected. Match either, so the click isn't silently skipped.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickTab(label: String) {
        // Waits out an in-flight screen transition, and retries if the node
        // is recycled between lookup and click (StaleObjectException).
        device.waitForIdle()
        repeat(3) {
            val tab = device.wait(Until.findObject(By.desc(label)), TIMEOUT)
                ?: device.findObject(By.text(label))
                ?: return
            try {
                tab.click()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                device.waitForIdle()
            }
        }
    }
}
