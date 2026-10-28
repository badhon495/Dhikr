package com.dhikr.app.core.localization

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A string added to values/ with no twin in values-bn/ falls back to English
 * silently, so a Bangla user just sees a stray English label. Fail the build
 * instead. Unit tests run with the module directory as the working directory.
 */
class StringParityTest {

    private val resourceName = Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"""")

    private fun keys(path: String): Set<String> {
        val file = File(path)
        assertTrue("missing $path (cwd=${File(".").absolutePath})", file.exists())
        return resourceName.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun every_english_string_has_a_bangla_translation() {
        val missing = keys("src/main/res/values/strings.xml") - keys("src/main/res/values-bn/strings.xml")
        assertTrue("Missing in values-bn: ${missing.sorted()}", missing.isEmpty())
    }

    @Test
    fun bangla_has_no_orphan_strings() {
        val orphans = keys("src/main/res/values-bn/strings.xml") - keys("src/main/res/values/strings.xml")
        assertTrue("In values-bn but not values: ${orphans.sorted()}", orphans.isEmpty())
    }
}
