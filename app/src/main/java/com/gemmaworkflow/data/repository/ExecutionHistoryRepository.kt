package com.gemmaworkflow.data.repository

import android.content.Context
import com.gemmaworkflow.domain.model.ExecutionLogEntry
import com.gemmaworkflow.domain.model.ExecutionResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Append-only execution log stored as a JSON array.
 *
 * Each workflow execution appends one entry. Keeps last 100 entries.
 *
 * Easer reference: /mnt/d/Easer/app/src/main/java/ryey/easer/core/log/ActivityLogService.java
 */
class ExecutionHistoryRepository(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "history/execution_log.json")
        .also { it.parentFile?.mkdirs() }

    /** Log a workflow execution. Appends to the history file. */
    fun log(workflowName: String, results: List<ExecutionResult>) {
        val entries = getAll().toMutableList()
        entries.add(ExecutionLogEntry(
            workflowName = workflowName,
            timestampMillis = System.currentTimeMillis(),
            results = results,
            allSuccess = results.all { it.success }
        ))
        val trimmed = if (entries.size > 100) entries.takeLast(100) else entries
        file.writeText(json.encodeToString(trimmed))
    }

    /** Get all logged executions (newest last). */
    fun getAll(): List<ExecutionLogEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ExecutionLogEntry>>(file.readText())
        }.getOrDefault(emptyList())
    }

    /** Get the N most recent executions. */
    fun recent(limit: Int = 20): List<ExecutionLogEntry> =
        getAll().takeLast(limit)

    /** Get executions for a specific workflow. */
    fun forWorkflow(name: String): List<ExecutionLogEntry> =
        getAll().filter { it.workflowName == name }

    /** Clear all history. */
    fun clear() {
        file.delete()
    }
}
