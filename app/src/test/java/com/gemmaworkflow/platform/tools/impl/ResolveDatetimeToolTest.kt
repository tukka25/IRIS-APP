package com.gemmaworkflow.platform.tools.impl

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveDatetimeToolTest {

    @Test
    fun resolvesNextFridayAtSixPmWithReferenceTime() = runBlocking {
        val result = ResolveDatetimeTool.execute(
            mapOf(
                "expression" to "next Friday at 6pm",
                "reference_time_iso" to "2026-05-06T10:00:00Z",
                "timezone" to "UTC"
            )
        )

        assertTrue(result.error.orEmpty(), result.success)
        assertTrue(result.output, result.output.contains("date: 2026-05-08"))
        assertTrue(result.output, result.output.contains("time: 18:00"))
        assertTrue(result.output, result.output.contains("unix_ms:"))
    }

    @Test
    fun resolvesSixOclockOnNextFridayWithReferenceTime() = runBlocking {
        val result = ResolveDatetimeTool.execute(
            mapOf(
                "expression" to "6 oclock on next friday",
                "reference_time_iso" to "2026-05-06T10:00:00Z",
                "timezone" to "UTC"
            )
        )

        assertTrue(result.error.orEmpty(), result.success)
        assertTrue(result.output, result.output.contains("date: 2026-05-08"))
        assertTrue(result.output, result.output.contains("time: 06:00"))
    }

    @Test
    fun doesNotMark24HourTimeAsAmbiguous() = runBlocking {
        val result = ResolveDatetimeTool.execute(
            mapOf(
                "expression" to "2026-05-15T18:00",
                "reference_time_iso" to "2026-05-06T10:00:00Z",
                "timezone" to "UTC",
                "default_period" to "pm"
            )
        )

        assertTrue(result.error.orEmpty(), result.success)
        assertTrue(result.output, result.output.contains("time: 18:00"))
        assertFalse(result.output, result.output.contains("assumption:"))
    }
}
