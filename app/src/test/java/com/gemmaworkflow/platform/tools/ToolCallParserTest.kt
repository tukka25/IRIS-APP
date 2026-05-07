package com.gemmaworkflow.platform.tools

import org.junit.Test
import org.junit.Assert.*

/**
 * Quick smoke tests for ToolCallParser and ToolRegistry.
 */
class ToolCallParserTest {

    @Test
    fun `parses valid tool call`() {
        val text = """Here is my plan. TOOL: get_current_time {}"""
        val call = ToolCallParser.findToolCall(text)
        assertNotNull("Should parse tool call", call)
        assertEquals("get_current_time", call?.name)
        assertTrue("Params should be empty", call?.params?.isEmpty() ?: false)
    }

    @Test
    fun `parses tool call with params`() {
        val text = """TOOL: resolve_datetime {"expression": "next Friday at 2pm"}"""
        val call = ToolCallParser.findToolCall(text)
        assertNotNull(call)
        assertEquals("resolve_datetime", call?.name)
        assertEquals("next Friday at 2pm", call?.params?.get("expression"))
    }

    @Test
    fun `parses tool call with multiple params`() {
        val text = """TOOL: search_places {"query": "coffee shop", "near": "Dubai"}"""
        val call = ToolCallParser.findToolCall(text)
        assertNotNull(call)
        assertEquals("search_places", call?.name)
        assertEquals("coffee shop", call?.params?.get("query"))
        assertEquals("Dubai", call?.params?.get("near"))
    }

    @Test
    fun `returns null for text without tool call`() {
        val text = "This is just a normal response without any tools."
        val call = ToolCallParser.findToolCall(text)
        assertNull("Should return null for no tool call", call)
    }

    @Test
    fun `returns null for malformed JSON params`() {
        val text = """TOOL: bad_tool {not valid json}"""
        val call = ToolCallParser.findToolCall(text)
        assertNull("Should return null for bad JSON", call)
    }

    @Test
    fun `formats successful tool result`() {
        val result = ToolResult(true, "iso: 2026-05-15T14:00:00+04:00")
        val formatted = ToolCallParser.formatResult("resolve_datetime", result)
        assertTrue(formatted.contains("TOOL_RESULT: resolve_datetime"))
        assertTrue(formatted.contains("2026-05-15"))
    }

    @Test
    fun `formats failed tool result`() {
        val result = ToolResult(false, "", "Expression not recognized")
        val formatted = ToolCallParser.formatResult("bad_tool", result)
        assertTrue(formatted.contains("TOOL_RESULT: bad_tool"))
        assertTrue(formatted.contains("ERROR"))
    }
}
