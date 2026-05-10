package com.gemmaworkflow.platform.tools.reto

/**
 * Validates that all blocking fact requirements have been resolved
 * before the workflow proceeds to action planning.
 *
 * Replaces the old "minimum tool call enforcement" — instead of
 * checking whether the model called all tools, we check whether
 * all required facts are satisfied.
 */
object CoverageValidator {

    /**
     * Check a requirement ledger for completeness.
     * Returns a list of issues. Empty list = all blocking facts resolved.
     */
    fun validate(ledger: RequirementLedger): CoverageResult {
        val missing = mutableListOf<FactRequirement>()
        val warnings = mutableListOf<FactRequirement>()

        for (req in ledger.requirements) {
            when (req.status) {
                RequirementStatus.PENDING -> {
                    if (req.blocking) missing.add(req) else warnings.add(req)
                }
                RequirementStatus.FAILED -> {
                    if (req.blocking) missing.add(req)
                }
                RequirementStatus.AMBIGUOUS -> {
                    warnings.add(req)  // needs user disambiguation
                }
                RequirementStatus.RESOLVED, RequirementStatus.SKIPPED -> {
                    // ok
                }
            }
        }

        val isComplete = missing.isEmpty()

        return CoverageResult(
            isComplete = isComplete,
            missingBlocking = missing,
            warnings = warnings
        )
    }

    data class CoverageResult(
        val isComplete: Boolean,
        val missingBlocking: List<FactRequirement>,
        val warnings: List<FactRequirement>
    ) {
        fun summary(): String = buildString {
            if (isComplete) {
                appendLine("All blocking requirements resolved.")
            } else {
                appendLine("BLOCKING REQUIREMENTS MISSING (${missingBlocking.size}):")
                missingBlocking.forEach { req ->
                    appendLine("  - ${req.id}: ${req.factType.label} for ${req.sourceAction}.${req.slot} (mention: \"${req.mention}\")")
                }
            }
            if (warnings.isNotEmpty()) {
                appendLine("Warnings (${warnings.size}):")
                warnings.forEach { req ->
                    appendLine("  - ${req.id}: ${req.factType.label} (${req.status})")
                }
            }
        }
    }
}
