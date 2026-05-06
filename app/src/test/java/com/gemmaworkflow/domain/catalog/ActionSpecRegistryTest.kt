package com.gemmaworkflow.domain.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionSpecRegistryTest {

    @Test
    fun promptSummaryExposesOnlyPromptSafeFields() {
        val summary = ActionSpecRegistry.toPromptSummary()

        assertTrue(summary.contains("browser.open_url"))
        assertTrue(summary.contains("calendar.create_event"))
        assertFalse(summary.contains("Intent.ACTION_SEND"))
        assertFalse(summary.contains("Intent.EXTRA_TEXT"))
        assertFalse(summary.contains("AlarmClock.EXTRA_HOUR"))
        assertFalse(summary.contains("CalendarContract.EXTRA_EVENT_BEGIN_TIME"))
    }
}
