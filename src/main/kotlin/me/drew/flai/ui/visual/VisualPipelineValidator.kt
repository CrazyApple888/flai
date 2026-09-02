package me.drew.flai.ui.visual

import me.drew.flai.domain.service.IssueScope
import me.drew.flai.domain.service.PipelineValidator

data class ValidationError(val gateId: String, val field: String, val message: String)

data class ValidationResult(val errors: List<ValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object VisualPipelineValidator {
    /**
     * Full validation: [validateStructure] first, then — only when the model can be
     * converted to a [me.drew.flai.domain.model.Pipeline] — every rule of the core
     * [PipelineValidator], mapped back to [ValidationError]s.
     */
    fun validate(model: VisualPipelineModel, validator: PipelineValidator = PipelineValidator()): ValidationResult {
        val structure = validateStructure(model)
        if (!structure.isValid) {
            return structure
        }
        val errors = validator.collectIssues(model.toPipeline()).map { issue ->
            ValidationError(issue.gateId ?: scopeLabel(issue.scope), issue.field, issue.message)
        }
        return ValidationResult(errors)
    }

    /**
     * Model-only checks — enough for the YAML to round-trip through the parser:
     * pipeline id, existing entry node, non-blank unique gate ids, edges pointing at
     * existing nodes. Used by auto-sync; gate semantics stay in [validate].
     */
    fun validateStructure(model: VisualPipelineModel): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        if (model.pipelineId.isBlank()) {
            errors.add(ValidationError("(pipeline)", "id", "Pipeline id is required"))
        }
        if (model.entryNodeSeq == -1 || model.nodeBySeq(model.entryNodeSeq) == null) {
            errors.add(ValidationError("(pipeline)", "entry", "Pipeline entry gate is required and must exist"))
        }
        val seen = mutableSetOf<String>()
        for (node in model.nodes) {
            val gateId = node.gateId
            if (gateId.isBlank()) {
                errors.add(ValidationError("(gate #${node.nodeSeq})", "id", "Gate id is required"))
                continue
            }
            if (!seen.add(gateId)) {
                errors.add(ValidationError(gateId, "id", "Gate id '$gateId' is used more than once"))
            }
        }
        for (edge in model.edges) {
            if (model.nodeBySeq(edge.fromSeq) == null) {
                errors.add(ValidationError("(edge)", "from", "Edge references non-existent source node"))
            }
            if (model.nodeBySeq(edge.toSeq) == null) {
                errors.add(ValidationError("(edge)", "to", "Edge references non-existent target node"))
            }
        }
        return ValidationResult(errors)
    }

    private fun scopeLabel(scope: IssueScope): String = when (scope) {
        IssueScope.PIPELINE -> "(pipeline)"
        IssueScope.GATE -> "(gate)"
        IssueScope.EDGE -> "(edge)"
    }
}
