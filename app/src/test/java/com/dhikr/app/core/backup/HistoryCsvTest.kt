package com.dhikr.app.core.backup

import com.dhikr.app.core.share.ProgressShareText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class HistoryCsvTest {

    @Test
    fun buildsSortedRowsInLocalTime() {
        val utc = TimeZone.getTimeZone("UTC")
        val csv = HistoryCsv.build(
            listOf(
                HistoryCsv.Row(86_400_000L, 86_460_000L, "Alhamdulillah", "Morning, Dhikr", 33),
                HistoryCsv.Row(0L, 0L, "SubhanAllah", null, 1),
            ),
            utc,
        )
        assertEquals(
            "date,time,dhikr,routine,count,duration_seconds\r\n" +
                "1970-01-01,00:00:00,SubhanAllah,,1,0\r\n" +
                "1970-01-02,00:00:00,Alhamdulillah,\"Morning, Dhikr\",33,60\r\n",
            csv,
        )
    }

    @Test
    fun escapesQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", HistoryCsv.escape("say \"hi\""))
    }

    @Test
    fun shareTextListsItemsAndTotal() {
        val text = ProgressShareText.build(
            "Today's Dhikr",
            listOf("SubhanAllah" to 100, "Alhamdulillah" to 33),
        ) { "Total: $it" }
        assertEquals("Today's Dhikr\n\nSubhanAllah — 100\nAlhamdulillah — 33\n\nTotal: 133", text)
    }
}
