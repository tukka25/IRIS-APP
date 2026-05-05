package com.gemmaworkflow.domain.safety

import com.gemmaworkflow.domain.catalog.ActionCatalog
import com.gemmaworkflow.domain.model.PlannedWorkflow
import com.gemmaworkflow.domain.model.WorkflowStep

/**
 * Validates a parsed workflow against the action allowlist.
 * Rejects unknown actions, missing params, unsupported triggers, and unsafe URLs.
 */
object WorkflowValidator {

    /** Allowed URL schemes. */
    private val allowedSchemes = setOf("https", "http", "geo", "spotify")

    /**
     * Validate a workflow. Returns a list of error messages.
     * An empty list means the workflow is valid and safe to run.
     */
    fun validate(workflow: PlannedWorkflow): List<String> {
        val errors = mutableListOf<String>()

        if (workflow.name.isBlank()) {
            errors.add("Workflow name is empty")
        }

        // Validate each action
        for ((index, step) in workflow.actions.withIndex()) {
            val prefix = "Action $index (${step.id})"

            // Check action exists in catalog
            val catalogAction = ActionCatalog.find(step.id)
            if (catalogAction == null) {
                errors.add("$prefix: unknown action id — not in allowlist")
                continue
            }

            // Check required params
            for ((paramName, paramInfo) in catalogAction.paramSchema) {
                if (paramInfo.required && step.params[paramName].isNullOrBlank()) {
                    errors.add("$prefix: missing required param '$paramName'")
                }
            }

            // Validate URL params
            if (step.id == "browser.open_url" || step.id == "maps.open_place") {
                step.params["url"]?.let { url ->
                    val scheme = url.substringBefore("://")
                    if (scheme !in allowedSchemes && !url.startsWith("geo:")) {
                        errors.add("$prefix: unsupported URL scheme '$scheme'")
                    }
                }
            }

            // Check for params not in the catalog (model hallucination)
            for (paramName in step.params.keys) {
                if (paramName !in catalogAction.paramSchema) {
                    errors.add("$prefix: unknown param '$paramName' (not in catalog)")
                }
            }
        }

        return errors
    }

    /**
     * Returns a set of action IDs that need user confirmation before running.
     */
    fun confirmationActions(steps: List<WorkflowStep>): Set<String> {
        return steps.filter { step ->
            ActionCatalog.find(step.id)?.needsConfirmation == true
        }.map { it.id }.toSet()
    }
}
