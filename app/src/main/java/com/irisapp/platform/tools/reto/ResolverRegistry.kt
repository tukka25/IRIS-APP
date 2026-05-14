package com.irisapp.platform.tools.reto

import com.irisapp.platform.tools.ToolRegistry

/**
 * Deterministically resolves fact requirements by calling the
 * appropriate tool from the registry.
 *
 * The SLM identifies WHAT facts are needed. The ResolverRegistry
 * guarantees they get resolved — no model decision involved.
 */
object ResolverRegistry {

    /**
     * Resolve a single fact requirement by calling its mapped tool.
     * Returns the tool output string or null on failure.
     */
    suspend fun resolve(requirement: FactRequirement): ResolutionResult {
        val resolver = requirement.resolverTool ?: requirement.factType.resolverTool
        val paramName = requirement.factType.resolverParam
        val mention = requirement.mention

        val tool = ToolRegistry.get(resolver)
        if (tool == null) {
            return ResolutionResult.Failed(requirement.id, "Unknown resolver: $resolver")
        }

        val input = if (requirement.toolArgs.isNotEmpty()) {
            sanitizeArgs(requirement.toolArgs, tool.parameters.map { it.name }.toSet())
        } else if (paramName.isNotEmpty()) {
            mapOf(paramName to mention)
        } else {
            emptyMap()
        }

        val result = ToolRegistry.execute(resolver, input)

        val metadata = ToolMetadataRegistry.get(resolver)
        val matchedFailureSignal = metadata?.failureSignals
            ?.firstOrNull { signal -> result.output.contains(signal, ignoreCase = true) }

        return if (result.success && matchedFailureSignal == null) {
            ResolutionResult.Resolved(
                requirementId = requirement.id,
                factType = requirement.factType,
                value = result.output
            )
        } else {
            ResolutionResult.Failed(
                requirementId = requirement.id,
                reason = result.error ?: matchedFailureSignal ?: "Tool execution failed"
            )
        }
    }

    private fun sanitizeArgs(
        args: Map<String, String>,
        allowedNames: Set<String>
    ): Map<String, String> {
        if (allowedNames.isEmpty()) return emptyMap()
        return args.filterKeys { it in allowedNames }
    }

    /**
     * Resolve every pending requirement in a ledger.
     *
     * Optional requirements are still resolved when the slot grounding agent
     * requested them, but CoverageValidator only blocks on requirements marked
     * blocking=true.
     */
    suspend fun resolvePending(ledger: RequirementLedger): RequirementLedger {
        for (req in ledger.requirements) {
            if (req.status != RequirementStatus.PENDING) continue

            val resolution = resolve(req)
            when (resolution) {
                is ResolutionResult.Resolved -> ledger.resolve(req.id, resolution.value)
                is ResolutionResult.Failed -> ledger.fail(req.id, resolution.reason)
            }
        }
        return ledger
    }

    suspend fun resolveBlocking(ledger: RequirementLedger): RequirementLedger = resolvePending(ledger)
}

sealed interface ResolutionResult {
    data class Resolved(
        val requirementId: String,
        val factType: FactType,
        val value: String
    ) : ResolutionResult

    data class Failed(
        val requirementId: String,
        val reason: String
    ) : ResolutionResult
}
