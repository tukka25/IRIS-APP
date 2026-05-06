package com.gemmaworkflow.domain.safety

import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.TriggerConfig
import com.gemmaworkflow.domain.model.WorkflowStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkflowValidatorTest {

    @Test
    fun acceptsValidTypedAction() {
        val workflow = PlannedWorkflow(
            name = "Open maps",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "maps.open_place",
                    params = buildJsonObject { put("query", "coffee near me") }
                )
            )
        )

        val errors = WorkflowValidator.validate(workflow, setOf("maps.open_place"))

        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun rejectsUnknownParamsAndWrongTypes() {
        val workflow = PlannedWorkflow(
            name = "Alarm",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "alarm.set_alarm",
                    params = buildJsonObject {
                        put("hour", "seven")
                        put("minutes", 30)
                        put("extra", "hallucinated")
                    },
                    requiresConfirmation = true
                )
            )
        )

        val errors = WorkflowValidator.validate(workflow, setOf("alarm.set_alarm"))

        assertTrue(errors.any { it.contains("hour") && it.contains("int") })
        assertTrue(errors.any { it.contains("unknown param 'extra'") })
    }

    @Test
    fun rejectsUnavailableAction() {
        val workflow = PlannedWorkflow(
            name = "Share",
            trigger = TriggerConfig.Manual,
            actions = listOf(
                WorkflowStep(
                    id = "share.share_text",
                    params = buildJsonObject { put("text", "hello") },
                    requiresConfirmation = true
                )
            )
        )

        val errors = WorkflowValidator.validate(workflow, availableActionIds = emptySet())

        assertTrue(errors.any { it.contains("not available") })
    }
}
