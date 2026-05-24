package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*

data class ValidationError(val gateId: String, val field: String, val message: String)

data class ValidationResult(val errors: List<ValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object VisualPipelineValidator {
    /**
     * Validates all required fields per FR-27 plus edge integrity (EC-10, EC-11).
     * Returns a ValidationResult; caller blocks Apply if !isValid.
     */
    fun validate(model: VisualPipelineModel): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        // Pipeline metadata
        if (model.pipelineId.isEmpty()) {
            errors.add(ValidationError("(pipeline)", "id", "Pipeline id is required"))
        }
        if (model.entryNodeSeq == -1 || model.nodeBySeq(model.entryNodeSeq) == null) {
            errors.add(ValidationError("(pipeline)", "entry", "Pipeline entry gate is required and must exist"))
        }

        // Gate-level validation
        for (node in model.nodes) {
            val gateId = node.gateId
            if (gateId.isEmpty()) {
                errors.add(ValidationError(gateId, "id", "Gate id is required"))
                continue
            }
            validateGate(node.gate, errors)
        }

        // Edge integrity (EC-10, EC-11)
        val gateIds = model.nodes.map { it.gateId }.toSet()
        for (edge in model.edges) {
            val fromNode = model.nodeBySeq(edge.fromSeq)
            val toNode = model.nodeBySeq(edge.toSeq)

            if (fromNode == null) {
                errors.add(ValidationError("(edge)", "from", "Edge references non-existent source node"))
                continue
            }
            if (toNode == null) {
                errors.add(ValidationError("(edge)", "to", "Edge references non-existent target node"))
                continue
            }

            // EC-11: validate fromPort exists on source gate
            val validOutputPorts = fromNode.gate.outputPorts()
            if (edge.fromPort !in validOutputPorts) {
                errors.add(ValidationError(
                    fromNode.gateId,
                    "fromPort",
                    "Edge fromPort '${edge.fromPort}' does not exist on gate '${fromNode.gateId}'"
                ))
            }
        }

        return ValidationResult(errors)
    }

    private fun validateGate(gate: Gate, errors: MutableList<ValidationError>) {
        val id = gate.id.value
        when (gate) {
            is InputGate -> {
                // Gate ID required (already checked above)
            }
            is OutputGate -> {
                // Gate ID required (already checked above)
            }
            is LlmGate -> {
                if (gate.promptTemplate.isEmpty()) {
                    errors.add(ValidationError(id, "promptTemplate", "promptTemplate is required for LlmGate '$id'"))
                }
                if (gate.endpointConfig.url.isEmpty()) {
                    errors.add(ValidationError(id, "endpointConfig.url", "endpoint url is required for LlmGate '$id'"))
                }
                if (gate.endpointConfig.credentialId.isEmpty() && gate.endpointConfig.apiKeyVar.isNullOrEmpty()) {
                    errors.add(ValidationError(id, "endpointConfig.credentialId", "endpoint must have credentialId or apiKeyVar for LlmGate '$id'"))
                }
                if (gate.endpointConfig.model.isEmpty()) {
                    errors.add(ValidationError(id, "endpointConfig.model", "endpoint model is required for LlmGate '$id'"))
                }
            }
            is LogicGate -> {
                if (gate.defaultPort.isNullOrEmpty()) {
                    errors.add(ValidationError(id, "defaultPort", "defaultPort is required for LogicGate '$id'"))
                }
                for (branch in gate.branches) {
                    if (branch.port.isEmpty()) {
                        errors.add(ValidationError(id, "branch.port", "Branch port is required for LogicGate '$id'"))
                    }
                    if (branch.condition == null) {
                        errors.add(ValidationError(id, "branch.condition", "Branch condition is required for LogicGate '$id'"))
                    }
                }
            }
            is ToolGate -> {
                if (gate.toolName.isEmpty()) {
                    errors.add(ValidationError(id, "toolName", "toolName is required for ToolGate '$id'"))
                }
            }
            is BashGate -> {
                if (gate.command.isBlank()) {
                    errors.add(ValidationError(id, "command", "command is required for BashGate '$id'"))
                }
                if (gate.workingDirectory.isBlank()) {
                    errors.add(ValidationError(id, "workingDirectory", "workingDirectory is required for BashGate '$id'"))
                }
                if (gate.timeoutSeconds <= 0) {
                    errors.add(ValidationError(id, "timeoutSeconds", "timeoutSeconds must be greater than zero for BashGate '$id'"))
                }
                if (gate.environment.keys.any { it.isBlank() }) {
                    errors.add(ValidationError(id, "environment", "environment keys must not be blank for BashGate '$id'"))
                }
                if (gate.outputMapping.keys.any { it.isBlank() } || gate.outputMapping.values.any { it.isBlank() }) {
                    errors.add(ValidationError(id, "outputMapping", "outputMapping keys and values must not be blank for BashGate '$id'"))
                }
            }
            is ReadFileGate -> {
                if (gate.path.isEmpty()) {
                    errors.add(ValidationError(id, "path", "path is required for ReadFileGate '$id'"))
                }
                if (gate.outputKey.isEmpty()) {
                    errors.add(ValidationError(id, "outputKey", "outputKey is required for ReadFileGate '$id'"))
                }
            }
            is WriteFileGate -> {
                if (gate.path.isEmpty()) {
                    errors.add(ValidationError(id, "path", "path is required for WriteFileGate '$id'"))
                }
                if (gate.contentKey.isEmpty()) {
                    errors.add(ValidationError(id, "contentKey", "contentKey is required for WriteFileGate '$id'"))
                }
                // mode always has a value (enum can't be null)
            }
        }
    }
}
