package me.drew.flai.domain.model

@JvmInline
value class GateId(val value: String)

sealed class Gate {
    abstract val id: GateId
    abstract val label: String
}

data class InputGate(
    override val id: GateId,
    override val label: String,
    val inputSchema: List<InputField> = emptyList(),
) : Gate()

data class InputField(
    val name: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String? = null,
)

enum class FieldType { STRING, NUMBER, BOOLEAN, JSON }

data class OutputGate(
    override val id: GateId,
    override val label: String,
    val outputMapping: Map<String, String> = emptyMap(),
) : Gate()

data class LlmGate(
    override val id: GateId,
    override val label: String,
    val promptTemplate: String,
    val inputMapping: Map<String, String> = emptyMap(),
    val outputMapping: Map<String, String> = mapOf("response" to "response"),
    val endpointConfig: LlmEndpointConfig,
) : Gate()

data class LlmEndpointConfig(
    val url: String,
    val credentialId: String,
    val model: String,
    val params: Map<String, Any?> = emptyMap(),
)

data class LogicGate(
    override val id: GateId,
    override val label: String,
    val branches: List<Branch>,
    val defaultPort: String? = "default",
) : Gate()

data class Branch(
    val port: String,
    val condition: BranchCondition,
)

sealed class BranchCondition {
    data class Comparison(
        val variable: String,
        val op: ComparisonOp,
        val value: String,
    ) : BranchCondition()

    data class SwitchCase(
        val variable: String,
        val values: List<String>,
    ) : BranchCondition()

    object Always : BranchCondition()
}

enum class ComparisonOp { EQ, NEQ, GT, GTE, LT, LTE, CONTAINS, STARTS_WITH }

data class ToolGate(
    override val id: GateId,
    override val label: String,
    val toolName: String,
    val inputMapping: Map<String, String> = emptyMap(),
    val outputMapping: Map<String, String> = emptyMap(),
) : Gate()

enum class WriteMode { OVERWRITE, APPEND, FAIL_IF_EXISTS }

data class ReadFileGate(
    override val id: GateId,
    override val label: String,
    val path: String,
    val outputKey: String = "content",
) : Gate()

data class WriteFileGate(
    override val id: GateId,
    override val label: String,
    val path: String,
    val contentKey: String,
    val mode: WriteMode = WriteMode.OVERWRITE,
) : Gate()
