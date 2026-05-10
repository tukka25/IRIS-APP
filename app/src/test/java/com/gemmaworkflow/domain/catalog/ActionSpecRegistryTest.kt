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

    @Test
    fun filtersActionsByLogicalCategory() {
        val sendMessageIds = ActionSpecRegistry.findByLogicalAction("send_message")
            .map { it.id }
            .toSet()

        assertTrue(sendMessageIds.contains("sms.compose"))
        assertTrue(sendMessageIds.contains("share.share_text"))
        assertFalse(sendMessageIds.contains("calendar.create_event"))
    }

    @Test
    fun checkNotificationHasNoDefaultCapability() {
        val actions = ActionSpecRegistry.findByLogicalAction("check_notification")

        assertTrue(actions.isEmpty())
    }

    @Test
    fun actionSpecsExposeScopedToolBindings() {
        val sms = ActionSpecRegistry.find("sms.compose")!!
        val smsTools = ActionSpecRegistry.toolNamesForAction(sms)
        val smsPhone = sms.params.single { it.name == "phone" }
        val smsMessage = sms.params.single { it.name == "message" }

        assertTrue(smsTools.contains("get_contact"))
        assertTrue(smsTools.contains("validate_json"))
        assertTrue(ActionSpecRegistry.resolverToolNamesForParam(sms, smsPhone).contains("get_contact"))
        assertFalse(ActionSpecRegistry.resolverToolNamesForParam(sms, smsMessage).contains("get_contact"))

        val calendar = ActionSpecRegistry.find("calendar.create_event")!!
        val beginTime = calendar.params.single { it.name == "begin_time_millis" }
        val title = calendar.params.single { it.name == "title" }

        assertTrue(ActionSpecRegistry.toolNamesForAction(calendar).contains("resolve_datetime"))
        assertTrue(ActionSpecRegistry.resolverToolNamesForParam(calendar, beginTime).contains("resolve_datetime"))
        assertFalse(ActionSpecRegistry.resolverToolNamesForParam(calendar, title).contains("resolve_datetime"))
    }
}
