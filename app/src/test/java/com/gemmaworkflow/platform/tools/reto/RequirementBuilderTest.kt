package com.gemmaworkflow.platform.tools.reto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequirementBuilderTest {

    @Test
    fun smsComposeCreatesContactRequirementFromMention() {
        val ledger = RequirementBuilder.build(
            userRequest = "send message to Maya saying hi",
            boundActions = listOf(
                CapabilityBinder.BoundAction(
                    taskId = "t1",
                    taskDescription = "Send a message to Maya",
                    actionId = "sms.compose",
                    status = CapabilityBinder.BindingStatus.SUPPORTED,
                    reason = "SMS can compose a message",
                    taskAction = "send_message",
                    taskTarget = "Maya",
                    entityMentions = listOf(TaskDecomposer.EntityMention("Maya", "contact"))
                )
            )
        )

        assertEquals(listOf("sms.compose"), ledger.actionCandidates)
        assertEquals(1, ledger.requirements.size)
        val requirement = ledger.requirements.single()
        assertEquals("phone", requirement.slot)
        assertEquals(FactType.CONTACT_PHONE, requirement.factType)
        assertEquals("Maya", requirement.mention)
        assertTrue(requirement.blocking)
    }

    @Test
    fun calendarEventCreatesDatetimeRequirementFromActionParams() {
        val ledger = RequirementBuilder.build(
            userRequest = "add meeting next Friday at 6 to my calendar",
            boundActions = listOf(
                CapabilityBinder.BoundAction(
                    taskId = "t1",
                    taskDescription = "Create calendar meeting",
                    actionId = "calendar.create_event",
                    status = CapabilityBinder.BindingStatus.SUPPORTED,
                    reason = "Calendar action can create events",
                    taskAction = "create_event",
                    taskTarget = "meeting",
                    timeMentions = listOf("next Friday at 6")
                )
            )
        )

        assertTrue(ledger.requirements.any {
            it.sourceAction == "calendar.create_event" &&
                it.slot == "begin_time_millis" &&
                it.factType == FactType.DATETIME_UNIX_MS &&
                it.mention == "next Friday at 6"
        })
    }

    @Test
    fun browserOpenUrlCreatesNoGroundingRequirement() {
        val ledger = RequirementBuilder.build(
            userRequest = "open https://example.com",
            boundActions = listOf(
                CapabilityBinder.BoundAction(
                    taskId = "t1",
                    taskDescription = "Open URL",
                    actionId = "browser.open_url",
                    status = CapabilityBinder.BindingStatus.SUPPORTED,
                    reason = "URL open action exists",
                    taskAction = "search",
                    taskTarget = "https://example.com"
                )
            )
        )

        assertTrue(ledger.requirements.isEmpty())
    }
}
