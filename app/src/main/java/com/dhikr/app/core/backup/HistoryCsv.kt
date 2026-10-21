package com.dhikr.app.core.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * History as CSV for spreadsheets: one row per logged session, local time.
 * Write-only — the JSON backup stays the format for restoring.
 */
object HistoryCsv {

    data class Row(
        val startedAt: Long,
        val endedAt: Long,
        val tasbihName: String,
        val routineName: String?,
        val count: Int,
    )

    fun build(rows: List<Row>, zone: TimeZone = TimeZone.getDefault()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = zone }
        return buildString {
            append("date,time,dhikr,routine,count,duration_seconds\r\n")
            rows.sortedBy { it.startedAt }.forEach { r ->
                val start = Date(r.startedAt)
                append(date.format(start)).append(',')
                append(time.format(start)).append(',')
                append(escape(r.tasbihName)).append(',')
                append(escape(r.routineName.orEmpty())).append(',')
                append(r.count).append(',')
                append(((r.endedAt - r.startedAt).coerceAtLeast(0L)) / 1000).append("\r\n")
            }
        }
    }

    /** RFC 4180: quote fields containing a comma, quote or line break. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
