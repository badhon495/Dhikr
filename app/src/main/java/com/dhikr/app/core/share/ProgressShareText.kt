package com.dhikr.app.core.share

/**
 * Plain-text "today's dhikr" summary for the share sheet (plan.md §42). Only
 * names and counts — nothing else about the user leaves the device, and only
 * when they choose to share it.
 */
object ProgressShareText {

    fun build(title: String, items: List<Pair<String, Int>>, totalLine: (Int) -> String): String =
        buildString {
            appendLine(title)
            appendLine()
            items.forEach { (name, count) -> appendLine("$name — $count") }
            appendLine()
            append(totalLine(items.sumOf { it.second }))
        }
}
