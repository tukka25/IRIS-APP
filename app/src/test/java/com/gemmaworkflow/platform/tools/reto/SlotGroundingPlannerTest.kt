package com.gemmaworkflow.platform.tools.reto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SlotGroundingPlannerTest {

    @Test
    fun needsToolSlotCreatesValidatedToolRequirement() {
        val ledger = SlotGroundingPlanner.buildLedger(
            userRequest = "send message to Maya saying hi",
            boundActions = listOf(sendMessageBinding()),
            slots = listOf(
                SlotGroundingPlanner.SlotGrounding(
                    taskId = "t1",
                    actionId = "sms.compose",
                    param = "phone",
                    status = SlotGroundingPlanner.SlotStatus.NEEDS_TOOL,
                    value = null,
                    tool = "get_contact",
                    toolArgs = mapOf("name" to "Maya"),
                    reason = "Maya is a contact"
                ),
                SlotGroundingPlanner.SlotGrounding(
                    taskId = "t1",
                    actionId = "sms.compose",
                    param = "message",
                    status = SlotGroundingPlanner.SlotStatus.LITERAL,
                    value = "hi",
                    tool = null,
                    toolArgs = emptyMap(),
                    reason = "message body is literal"
                )
            )
        )

        assertEquals(1, ledger.requirements.size)
        val requirement = ledger.requirements.single()
        assertEquals("phone", requirement.slot)
        assertEquals("get_contact", requirement.resolverTool)
        assertEquals(mapOf("name" to "Maya"), requirement.toolArgs)
        assertTrue(requirement.blocking)
        assertEquals(1, ledger.literalSlots.size)
        assertEquals("message", ledger.literalSlots.single().slot)
        assertEquals("hi", ledger.literalSlots.single().value)
    }

    @Test
    fun compactSummaryPromotesResolvedFactsToActionParams() {
        val phoneRequirement = FactRequirement(
            id = "r1",
            sourceAction = "sms.compose",
            slot = "phone",
            mention = "Maya",
            factType = FactType.CONTACT_PHONE,
            status = RequirementStatus.RESOLVED,
            resolvedValue = "Maya | phone: [+15550101001] | email: none"
        )
        val timeRequirement = FactRequirement(
            id = "r2",
            sourceAction = "calendar.create_event",
            slot = "begin_time_millis",
            mention = "next Friday at 6pm",
            factType = FactType.DATETIME_UNIX_MS,
            status = RequirementStatus.RESOLVED,
            resolvedValue = "iso: 2026-05-15T18:00:00+04:00\nunix_ms: 1778810400000"
        )
        val ledger = RequirementLedger(
            actionCandidates = listOf("sms.compose", "calendar.create_event"),
            requirements = listOf(phoneRequirement, timeRequirement),
            literalSlots = listOf(
                GroundedSlotValue(
                    sourceAction = "sms.compose",
                    slot = "message",
                    value = "hi"
                )
            )
        )

        val summary = ledger.compactSummary()

        assertTrue(summary.contains("sms.compose.message = hi"))
        assertTrue(summary.contains("sms.compose.phone = +15550101001"))
        assertTrue(summary.contains("calendar.create_event.begin_time_millis = 1778810400000"))
    }

    @Test
    fun invalidToolChoiceFallsBackToSafeRequirementBuilder() {
        val ledger = SlotGroundingPlanner.buildLedger(
            userRequest = "send message to Maya saying hi",
            boundActions = listOf(sendMessageBinding()),
            slots = listOf(
                SlotGroundingPlanner.SlotGrounding(
                    taskId = "t1",
                    actionId = "sms.compose",
                    param = "phone",
                    status = SlotGroundingPlanner.SlotStatus.NEEDS_TOOL,
                    value = null,
                    tool = "web_search",
                    toolArgs = mapOf("query" to "Maya"),
                    reason = "wrong tool"
                )
            )
        )

        assertTrue(ledger.requirements.any {
            it.slot == "phone" &&
                it.factType == FactType.CONTACT_PHONE &&
                it.resolverTool == null
        })
    }

    private fun sendMessageBinding(): CapabilityBinder.BoundAction =
        CapabilityBinder.BoundAction(
            taskId = "t1",
            taskDescription = "Send a message to Maya",
            actionId = "sms.compose",
            status = CapabilityBinder.BindingStatus.SUPPORTED,
            reason = "SMS can compose messages",
            taskAction = "send_message",
            taskTarget = "Maya",
            entityMentions = listOf(TaskDecomposer.EntityMention("Maya", "contact"))
        )
}
