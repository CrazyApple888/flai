package me.drew.flai.domain.service

import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.LogicGate
import me.drew.flai.domain.model.OutputGate
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.ReadFileGate
import me.drew.flai.domain.model.ToolGate
import me.drew.flai.domain.model.WriteFileGate
import me.drew.flai.domain.model.inputPorts
import me.drew.flai.domain.model.outputPorts

enum class IssueScope { PIPELINE, GATE, EDGE }

/** gateId is null for PIPELINE/EDGE scope issues that are not attributable to one gate. */
data class ValidationIssue(val scope: IssueScope, val gateId: String?, val field: String, val message: String)

class PipelineValidationException(val issues: List<ValidationIssue>) :
    Exception(issues.joinToString("; ") { it.message })

class PipelineValidator {

    /** Never throws. Order: pipeline-level, per-gate (in map order), edges, then cycles. */
    fun collectIssues(pipeline: Pipeline): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        collectPipelineIssues(pipeline, issues)
        for (gate in pipeline.gates.values) {
            collectGateIssues(gate, issues)
        }
        val allEdgeGatesExist = collectEdgeIssues(pipeline, issues)
        if (allEdgeGatesExist && hasCycle(pipeline)) {
            issues.add(ValidationIssue(IssueScope.PIPELINE, null, "edges", "Pipeline contains a cycle — DAG required"))
        }
        return issues
    }

    /** Throws PipelineValidationException with all issues if any. */
    fun validate(pipeline: Pipeline) {
        val issues = collectIssues(pipeline)
        if (issues.isNotEmpty()) {
            throw PipelineValidationException(issues)
        }
    }

    private fun collectPipelineIssues(pipeline: Pipeline, issues: MutableList<ValidationIssue>) {
        if (pipeline.id.value.isBlank()) {
            issues.add(ValidationIssue(IssueScope.PIPELINE, null, "id", "Pipeline id is required"))
        }
        if (pipeline.entryGateId !in pipeline.gates) {
            issues.add(
                ValidationIssue(
                    IssueScope.PIPELINE,
                    null,
                    "entry",
                    "Entry gate '${pipeline.entryGateId.value}' not found",
                ),
            )
        }
    }

    private fun collectGateIssues(gate: Gate, issues: MutableList<ValidationIssue>) {
        val id = gate.id.value
        fun issue(field: String, message: String) {
            issues.add(ValidationIssue(IssueScope.GATE, id, field, message))
        }
        if (!GATE_ID_PATTERN.matches(id)) {
            issue("id", "Gate id '$id' is invalid: only letters, digits, '_', '.' and '-' are allowed")
        }
        when (gate) {
            is InputGate -> Unit
            is OutputGate -> Unit
            is LlmGate -> {
                if (gate.promptTemplate.isBlank()) {
                    issue("promptTemplate", "promptTemplate is required for LlmGate '$id'")
                }
                if (gate.endpointConfig.url.isBlank()) {
                    issue("endpointConfig.url", "endpoint url is required for LlmGate '$id'")
                }
                if (gate.endpointConfig.model.isBlank()) {
                    issue("endpointConfig.model", "endpoint model is required for LlmGate '$id'")
                }
                if (gate.endpointConfig.credentialId.isBlank() && gate.endpointConfig.apiKeyVar.isNullOrBlank()) {
                    issue("endpointConfig.credentialId", "endpoint must have credentialId or apiKeyVar for LlmGate '$id'")
                }
                if (gate.maxToolRounds <= 0) {
                    issue("maxToolRounds", "maxToolRounds must be greater than zero for LlmGate '$id'")
                }
                if (gate.tools.any { it.isBlank() }) {
                    issue("tools", "tool names must not be blank for LlmGate '$id'")
                }
                if (gate.tools.distinct().size != gate.tools.size) {
                    issue("tools", "tool names must not be duplicated for LlmGate '$id'")
                }
            }
            is LogicGate -> {
                if (gate.defaultPort.isNullOrBlank()) {
                    issue("defaultPort", "defaultPort is required for LogicGate '$id'")
                }
                if (gate.branches.any { it.port.isBlank() }) {
                    issue("branch.port", "Branch port is required for LogicGate '$id'")
                }
            }
            is ToolGate -> {
                if (gate.toolName.isBlank()) {
                    issue("toolName", "toolName is required for ToolGate '$id'")
                }
            }
            is BashGate -> {
                if (gate.command.isBlank()) {
                    issue("command", "command is required for BashGate '$id'")
                }
                if (gate.workingDirectory.isBlank()) {
                    issue("workingDirectory", "workingDirectory is required for BashGate '$id'")
                }
                if (gate.timeoutSeconds <= 0) {
                    issue("timeoutSeconds", "timeoutSeconds must be greater than zero for BashGate '$id'")
                }
                if (gate.environment.keys.any { it.isBlank() }) {
                    issue("environment", "environment keys must not be blank for BashGate '$id'")
                }
                if (gate.outputMapping.keys.any { it.isBlank() } || gate.outputMapping.values.any { it.isBlank() }) {
                    issue("outputMapping", "outputMapping keys and values must not be blank for BashGate '$id'")
                }
            }
            is ReadFileGate -> {
                if (gate.path.isBlank()) {
                    issue("path", "path is required for ReadFileGate '$id'")
                }
                if (gate.outputKey.isBlank()) {
                    issue("outputKey", "outputKey is required for ReadFileGate '$id'")
                }
            }
            is WriteFileGate -> {
                if (gate.path.isBlank()) {
                    issue("path", "path is required for WriteFileGate '$id'")
                }
                if (gate.contentKey.isBlank()) {
                    issue("contentKey", "contentKey is required for WriteFileGate '$id'")
                }
            }
        }
    }

    /** Returns true when every edge references gates that exist. */
    private fun collectEdgeIssues(pipeline: Pipeline, issues: MutableList<ValidationIssue>): Boolean {
        var allGatesExist = true
        val seenOutgoing = mutableSetOf<Pair<GateId, String>>()
        for (edge in pipeline.edges) {
            val from = pipeline.gates[edge.from]
            val to = pipeline.gates[edge.to]
            if (from == null) {
                allGatesExist = false
                issues.add(
                    ValidationIssue(
                        IssueScope.EDGE,
                        edge.from.value,
                        "from",
                        "Edge references unknown gate '${edge.from.value}'",
                    ),
                )
            }
            if (to == null) {
                allGatesExist = false
                issues.add(
                    ValidationIssue(
                        IssueScope.EDGE,
                        edge.to.value,
                        "to",
                        "Edge references unknown gate '${edge.to.value}'",
                    ),
                )
            }
            if (from == null || to == null) {
                continue
            }
            if (edge.fromPort !in from.outputPorts()) {
                issues.add(
                    ValidationIssue(
                        IssueScope.EDGE,
                        from.id.value,
                        "fromPort",
                        "Edge fromPort '${edge.fromPort}' does not exist on gate '${from.id.value}'",
                    ),
                )
            }
            if (edge.toPort !in to.inputPorts()) {
                issues.add(
                    ValidationIssue(
                        IssueScope.EDGE,
                        to.id.value,
                        "toPort",
                        "Edge toPort '${edge.toPort}' does not exist on gate '${to.id.value}'",
                    ),
                )
            }
            if (!seenOutgoing.add(edge.from to edge.fromPort)) {
                issues.add(
                    ValidationIssue(
                        IssueScope.EDGE,
                        from.id.value,
                        "fromPort",
                        "Gate '${from.id.value}' already has an edge from port '${edge.fromPort}'",
                    ),
                )
            }
        }
        return allGatesExist
    }

    // Kahn's algorithm cycle detection; assumes every edge references existing gates.
    private fun hasCycle(pipeline: Pipeline): Boolean {
        val inDegree = mutableMapOf<GateId, Int>()
        pipeline.gates.keys.forEach { inDegree[it] = 0 }
        pipeline.edges.forEach { edge -> inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1 }

        val queue = ArrayDeque<GateId>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        var visited = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visited++
            pipeline.edges.filter { it.from == node }.forEach { edge ->
                val remaining = (inDegree[edge.to] ?: throw IllegalStateException("Gate ${edge.to.value} not in inDegree map")) - 1
                inDegree[edge.to] = remaining
                if (remaining == 0) {
                    queue.add(edge.to)
                }
            }
        }

        return visited != pipeline.gates.size
    }

    private companion object {
        val GATE_ID_PATTERN = Regex("[A-Za-z0-9_.-]+")
    }
}
