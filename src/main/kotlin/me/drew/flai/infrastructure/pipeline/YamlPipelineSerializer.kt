package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.*

class YamlPipelineSerializer {
    /**
     * Serialises a Pipeline to canonical block-style YAML.
     * Key ordering matches YamlPipelineParser expectations:
     *   top-level: id, name, description, entry, gates, edges
     *   gate: type, label, then type-specific fields
     */
    fun serialize(pipeline: Pipeline): String {
        val sb = StringBuilder()
        sb.appendLine("id: ${escapeScalar(pipeline.id.value)}")
        sb.appendLine("name: ${escapeScalar(pipeline.name)}")
        if (pipeline.description.isNotEmpty()) {
            sb.appendLine("description: ${escapeScalar(pipeline.description)}")
        }
        sb.appendLine("entry: ${escapeScalar(pipeline.entryGateId.value)}")
        sb.appendLine("gates:")
        for ((gateId, gate) in pipeline.gates) {
            sb.appendLine("  ${gateId.value}:")
            appendGate(sb, gate)
        }
        sb.appendLine("edges:")
        for (edge in pipeline.edges) {
            sb.append("  - from: ${escapeScalar(edge.from.value)}")
            sb.appendLine()
            if (edge.fromPort != "out") {
                sb.appendLine("    fromPort: ${escapeScalar(edge.fromPort)}")
            }
            sb.appendLine("    to: ${escapeScalar(edge.to.value)}")
            if (edge.toPort != "in") {
                sb.appendLine("    toPort: ${escapeScalar(edge.toPort)}")
            }
        }
        return sb.toString()
    }

    private fun appendGate(sb: StringBuilder, gate: Gate) {
        when (gate) {
            is InputGate -> {
                sb.appendLine("    type: input")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                if (gate.inputSchema.isNotEmpty()) {
                    sb.appendLine("    schema:")
                    for (field in gate.inputSchema) {
                        sb.appendLine("      - name: ${escapeScalar(field.name)}")
                        sb.appendLine("        type: ${field.type.name}")
                        if (!field.required) {
                            sb.appendLine("        required: false")
                        }
                        if (field.default != null) {
                            sb.appendLine("        default: ${escapeScalar(field.default)}")
                        }
                    }
                }
            }
            is OutputGate -> {
                sb.appendLine("    type: output")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                if (gate.outputMapping.isNotEmpty()) {
                    sb.appendLine("    outputMapping:")
                    for ((k, v) in gate.outputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
            }
            is LlmGate -> {
                sb.appendLine("    type: llm")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                appendMultilineString(sb, "    promptTemplate", gate.promptTemplate)
                if (gate.skills.isNotEmpty()) {
                    sb.appendLine("    skills:")
                    for (skill in gate.skills) {
                        sb.appendLine("      - ${escapeScalar(skill)}")
                    }
                }
                if (gate.inputMapping.isNotEmpty()) {
                    sb.appendLine("    inputMapping:")
                    for ((k, v) in gate.inputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
                val defaultOutputMapping = mapOf("response" to "response")
                if (gate.outputMapping != defaultOutputMapping) {
                    sb.appendLine("    outputMapping:")
                    for ((k, v) in gate.outputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
                sb.appendLine("    endpoint:")
                sb.appendLine("      url: ${escapeScalar(gate.endpointConfig.url)}")
                if (gate.endpointConfig.credentialId.isNotBlank()) {
                    sb.appendLine("      credentialId: ${escapeScalar(gate.endpointConfig.credentialId)}")
                }
                gate.endpointConfig.apiKeyVar?.let {
                    sb.appendLine("      apiKeyVar: ${escapeScalar(it)}")
                }
                sb.appendLine("      model: ${escapeScalar(gate.endpointConfig.model)}")
                if (gate.endpointConfig.params.isNotEmpty()) {
                    sb.appendLine("      params:")
                    for ((k, v) in gate.endpointConfig.params) {
                        sb.appendLine("        ${escapeScalar(k)}: ${escapeParamValue(v)}")
                    }
                }
            }
            is LogicGate -> {
                sb.appendLine("    type: logic")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                sb.appendLine("    branches:")
                for (branch in gate.branches) {
                    sb.appendLine("      - port: ${escapeScalar(branch.port)}")
                    sb.appendLine("        condition:")
                    appendCondition(sb, branch.condition)
                }
                if (gate.defaultPort != "default") {
                    if (gate.defaultPort != null) {
                        sb.appendLine("    defaultPort: ${escapeScalar(gate.defaultPort)}")
                    }
                }
            }
            is ToolGate -> {
                sb.appendLine("    type: tool")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                sb.appendLine("    tool: ${escapeScalar(gate.toolName)}")
                if (gate.inputMapping.isNotEmpty()) {
                    sb.appendLine("    inputMapping:")
                    for ((k, v) in gate.inputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
                if (gate.outputMapping.isNotEmpty()) {
                    sb.appendLine("    outputMapping:")
                    for ((k, v) in gate.outputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
            }
            is BashGate -> {
                sb.appendLine("    type: bash")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                appendMultilineString(sb, "    command", gate.command)
                if (gate.workingDirectory != ".") {
                    sb.appendLine("    workingDirectory: ${escapeScalar(gate.workingDirectory)}")
                }
                if (gate.environment.isNotEmpty()) {
                    sb.appendLine("    environment:")
                    for ((k, v) in gate.environment) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
                if (gate.timeoutSeconds != 120) {
                    sb.appendLine("    timeoutSeconds: ${gate.timeoutSeconds}")
                }
                if (!gate.failOnNonZeroExit) {
                    sb.appendLine("    failOnNonZeroExit: false")
                }
                if (gate.outputMapping.isNotEmpty()) {
                    sb.appendLine("    outputMapping:")
                    for ((k, v) in gate.outputMapping) {
                        sb.appendLine("      ${escapeScalar(k)}: ${escapeScalar(v)}")
                    }
                }
            }
            is ReadFileGate -> {
                sb.appendLine("    type: read-file")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                sb.appendLine("    path: ${escapeScalar(gate.path)}")
                if (gate.outputKey != "content") {
                    sb.appendLine("    outputKey: ${escapeScalar(gate.outputKey)}")
                }
            }
            is WriteFileGate -> {
                sb.appendLine("    type: write-file")
                sb.appendLine("    label: ${escapeScalar(gate.label)}")
                sb.appendLine("    path: ${escapeScalar(gate.path)}")
                sb.appendLine("    contentKey: ${escapeScalar(gate.contentKey)}")
                if (gate.mode != WriteMode.OVERWRITE) {
                    sb.appendLine("    mode: ${writeModeToString(gate.mode)}")
                }
            }
        }
    }

    private fun appendCondition(sb: StringBuilder, condition: BranchCondition) {
        when (condition) {
            is BranchCondition.Always -> {
                sb.appendLine("          type: always")
            }
            is BranchCondition.Comparison -> {
                sb.appendLine("          type: comparison")
                sb.appendLine("          variable: ${escapeScalar(condition.variable)}")
                sb.appendLine("          op: ${condition.op.name}")
                sb.appendLine("          value: ${escapeScalar(condition.value)}")
            }
            is BranchCondition.SwitchCase -> {
                sb.appendLine("          type: switch")
                sb.appendLine("          variable: ${escapeScalar(condition.variable)}")
                sb.appendLine("          values:")
                for (v in condition.values) {
                    sb.appendLine("            - ${escapeScalar(v)}")
                }
            }
        }
    }

    private fun appendMultilineString(sb: StringBuilder, key: String, value: String) {
        if (value.contains('\n')) {
            sb.appendLine("$key: |")
            for (line in value.split('\n')) {
                sb.appendLine("      $line")
            }
        } else {
            sb.appendLine("$key: ${escapeScalar(value)}")
        }
    }

    private fun escapeScalar(value: String): String {
        if (value.isEmpty()) return "''"
        val lower = value.lowercase()
        // YAML 1.1 boolean and null words that SnakeYAML parses as non-strings
        val yaml11Reserved = setOf(
            "true", "false", "yes", "no", "on", "off", "null", "~"
        )
        val needsQuoting = value.contains(':') ||
            value.contains('#') ||
            value.contains('\'') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r') ||
            value.startsWith(' ') ||
            value.endsWith(' ') ||
            value.startsWith('{') ||
            value.startsWith('[') ||
            lower in yaml11Reserved ||
            value.toIntOrNull() != null ||
            value.toDoubleOrNull() != null
        if (!needsQuoting) {
            return value
        }
        return "'${value.replace("'", "''")}'"
    }

    private fun escapeParamValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> escapeScalar(value)
            else -> escapeScalar(value.toString())
        }
    }

    private fun writeModeToString(mode: WriteMode): String = when (mode) {
        WriteMode.OVERWRITE -> "overwrite"
        WriteMode.APPEND -> "append"
        WriteMode.FAIL_IF_EXISTS -> "fail-if-exists"
    }
}
