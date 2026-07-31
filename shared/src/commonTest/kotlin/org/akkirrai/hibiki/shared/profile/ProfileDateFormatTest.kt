package org.akkirrai.hibiki.shared.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

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
}
