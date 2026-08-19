package org.akkirrai.hibiki.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.datetime.toLocalDateTime

class ProfileDateFormatTest {
    @Test
    fun recentDateUsesAndroidRelativeLabels() {
        val now = Clock.System.now().epochSeconds
        val labels = { days: Int -> "$days d ago" }

        assertEquals(
            "Today",
            profileRecentDateLabel(now, todayLabel = "Today", yesterdayLabel = "Yesterday", daysAgoLabel = labels),
        )
        assertEquals(
            "Yesterday",
            profileRecentDateLabel(now - 86_400L, todayLabel = "Today", yesterdayLabel = "Yesterday", daysAgoLabel = labels),
        )
        assertEquals(
            "3 d ago",
            profileRecentDateLabel(now - 3 * 86_400L, todayLabel = "Today", yesterdayLabel = "Yesterday", daysAgoLabel = labels),
        )
    }

    @Test
    fun addedDateUsesTheLocalCalendarDay() {
        val now = Clock.System.now()
        val expected = now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        val label = profileAddedDateLabel(
            value = now.epochSeconds,
            languageTag = "en",
        )

        val months = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        assertEquals("${expected.dayOfMonth} ${months[expected.month.ordinal]}", label)
    }
}
