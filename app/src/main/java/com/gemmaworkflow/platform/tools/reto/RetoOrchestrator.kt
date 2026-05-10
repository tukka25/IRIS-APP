package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.google.ai.edge.litertlm.Engine

/**
 * RETO orchestrator — capability-first requirement-led pipeline.
 *
 * Phase 0: Logical task decomposition (no tools, no catalog) — TaskDecomposer
 * Phase 1: Capability binding (map tasks → supported actions) — CapabilityBinder
 * Phase 2: Slot grounding + deterministic resolver execution
 *
 * Action plan + final JSON are handled by RetoWorkflowPlanner after this
 * returns a compact, validated RequirementLedger.
 */
class RetoOrchestrator(private val engine: Engine) {
    private companion object {
        const val TAG = "RetoOrchestrator"
    }

    data class OrchestrateResult(
        val sketch: RetoLayerSketch,
        val ledger: RequirementLedger,
        val compactSummary: String,
        val trace: RetoTrace,
        val debugTrace: String,
        // Phase 0+1 results
        val decomposition: TaskDecomposer.DecompositionResult?,
        val binding: CapabilityBinder.BindingResult?,
        val grounding: SlotGroundingPlanner.GroundingResult?
    )

    suspend fun orchestrate(
        userRequest: String,
        availableActionIds: Set<String> = emptySet(),
        onPhase: (String, String) -> Unit = { _, _ -> }
    ): OrchestrateResult {
        Log.i(TAG, "Starting orchestration for: ${userRequest.take(80)}")

        // ═══ Phase 0: Logical Task Decomposition ═══
        // No tools. No action catalog. Pure NLU.
        Log.i(TAG, "Phase 0: Decomposing logical tasks")
        onPhase("Phase 0 — Decompose", "Identifying logical tasks...")
        val decomposition = TaskDecomposer.decompose(engine, userRequest)
        onPhase("Phase 0 — Tasks", "${decomposition.tasks.size} tasks: ${decomposition.tasks.joinToString { "${it.id}=${it.action}" }}")

        // ═══ Phase 1: Capability Binding ═══
        // Map logical tasks to supported actions.
        Log.i(TAG, "Phase 1: Binding tasks to capabilities")
        onPhase("Phase 1 — Bind", "Mapping ${decomposition.tasks.size} tasks to available actions...")
        val binding = CapabilityBinder.bind(
            engine = engine,
            tasks = decomposition.tasks,
            availableActionIds = availableActionIds.ifEmpty { com.gemmaworkflow.domain.catalog.ActionSpecRegistry.allIds }
        )
        val actionable = binding.actionableBindings
        onPhase("Phase 1 — Bound", "${actionable.size}/${decomposition.tasks.size} tasks bound: ${actionable.joinToString { "${it.taskId}→${it.actionId}" }}")
        val unsupported = binding.boundActions.filter { it.status == CapabilityBinder.BindingStatus.UNSUPPORTED }
        if (unsupported.isNotEmpty()) {
            onPhase("Phase 1 — Unsupported", "${unsupported.size} tasks unsupported: ${unsupported.joinToString { it.taskId }}")
        }

        // ═══ Phase 2: Slot Grounding + Resolution ═══
        // The model decides literal vs tool vs missing per selected action param.
        // Kotlin validates tool choices against ActionSpec param metadata.
        Log.i(TAG, "Phase 2: Grounding slots for ${actionable.size} bound actions")
        onPhase("Phase 2 — Slot Grounding", "Planning param grounding for ${actionable.size} actions...")
        val grounding = SlotGroundingPlanner.plan(engine, userRequest, actionable)
        val ledger = grounding.ledger
        if (grounding.fallbackUsed) {
            onPhase("Phase 2 — Slot Grounding Fallback", "Used deterministic requirement builder")
        } else {
            onPhase("Phase 2 — Slots", "${grounding.slots.size} slot decisions")
        }

        if (ledger.requirements.isNotEmpty()) {
            onPhase("Phase 2 — Extracted", "${ledger.requirements.size} requirements: ${ledger.requirements.joinToString { "${it.id}=${it.factType.label}" }}")
        }

        // Resolve planned facts deterministically. Optional facts can still be
        // resolved when the slot grounding agent asked for them; coverage only
        // blocks on requirements marked blocking=true.
        val pendingRequirements = ledger.requirements.filter { it.status == RequirementStatus.PENDING }
        if (pendingRequirements.isNotEmpty()) {
            onPhase("Phase 2 — Resolving", "Resolving ${pendingRequirements.size} planned requirements...")
            ResolverRegistry.resolvePending(ledger)
            val coverage = CoverageValidator.validate(ledger)
            onPhase("Phase 2 — Coverage", coverage.summary())
        }

        val sketch = RetoLayerSketch(
            request = userRequest,
            layers = emptyList(),
            requirementLedger = ledger
        )
        val trace = RetoTrace(
            request = userRequest,
            layerSketch = sketch,
            observations = emptyList(),
            repairs = emptyList(),
            finalWorkflowJson = null
        )

        Log.i(TAG, "Orchestration complete")

        return OrchestrateResult(
            sketch = sketch,
            ledger = ledger,
            compactSummary = ledger.compactSummary(),
            trace = trace,
            debugTrace = buildString {
                appendLine("=== PHASE 0 — TASK DECOMPOSITION ===")
                appendLine(decomposition.rawOutput.take(500))
                appendLine()
                appendLine("=== PHASE 1 — CAPABILITY BINDING ===")
                appendLine(binding.rawOutput.take(500))
                appendLine()
                appendLine("=== PHASE 2 — REQUIREMENT LEDGER ===")
                appendLine("Slot grounding raw:")
                appendLine(grounding.rawOutput.take(500))
                appendLine()
                appendLine("Actions: ${ledger.actionCandidates.joinToString()}")
                ledger.literalSlots.forEach { slot ->
                    appendLine("  literal ${slot.sourceAction}.${slot.slot} = ${slot.value}")
                }
                ledger.requirements.forEach { req ->
                    appendLine("  ${req.id}: ${req.factType.label} via ${req.resolverTool ?: req.factType.resolverTool} → ${req.status}")
                }
            },
            decomposition = decomposition,
            binding = binding,
            grounding = grounding
        )
    }
}
