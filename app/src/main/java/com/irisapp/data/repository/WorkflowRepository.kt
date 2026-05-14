package com.irisapp.data.repository

import android.content.Context
import com.irisapp.data.local.storage.JsonFileStorage
import com.irisapp.domain.model.PlannedWorkflow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists PlannedWorkflow objects as JSON files under files/workflows/.
 *
 * Based on Easer's ScriptDataStorage pattern.
 * Easer reference: /mnt/d/Easer/app/src/main/java/ryey/easer/core/data/storage/ScriptDataStorage.java
 */
class WorkflowRepository(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    private val storage = JsonFileStorage(
        context = context,
        subdirectory = "workflows",
        serializer = { workflow: PlannedWorkflow -> json.encodeToString(workflow) },
        parser = { raw: String -> json.decodeFromString(raw) }
    )

    /** List names of all saved workflows. */
    fun listNames(): List<String> = storage.list()

    /** Load all saved workflows. */
    fun loadAll(): List<PlannedWorkflow> = listNames().mapNotNull { get(it) }

    /** Load a workflow by name. */
    fun get(name: String): PlannedWorkflow? = storage.get(name)

    /** Save (create or update) a workflow. */
    fun save(workflow: PlannedWorkflow) {
        storage.save(workflow.name, workflow)
    }

    /** Delete a workflow by name. */
    fun delete(name: String): Boolean = storage.delete(name)

    /** Check if a workflow exists. */
    fun exists(name: String): Boolean = storage.exists(name)
}
