package com.gemmaworkflow.platform.tools

import com.gemmaworkflow.platform.tools.impl.ResolveDatetimeTool
import org.junit.Assert.assertTrue
import org.junit.Test

class FindSkillTest {

    @Test
    fun schemaForInjectsToolExamplesFromMetadata() {
        ToolRegistry.register(ResolveDatetimeTool)

        val schema = FindSkill.schemaFor(setOf("resolve_datetime"))

        assertTrue(schema.contains("Tool: parse_relative_datetime (call as: resolve_datetime)"))
        assertTrue(schema.contains("Examples:"))
        assertTrue(schema.contains("TOOL: resolve_datetime"))
        assertTrue(schema.contains("\"expression\":\"next Friday at 6pm\""))
        assertTrue(schema.contains("\"reference_time_iso\""))
        assertTrue(schema.contains("\"timezone\""))
    }
}
