package me.drew.flai.domain.model

@JvmInline
value class GateId(val value: String)

sealed class Gate {
    abstract val id: GateId
    abstract val label: String
    abstract val faultTolerant: Boolean

    abstract fun withId(id: GateId): Gate
    abstract fun withLabel(label: String): Gate
    abstract fun withFaultTolerant(faultTolerant: Boolean): Gate
}

data class InputGate(
    override val id: GateId,
    override val label: String,
    val inputSchema: List<InputField> = emptyList(),
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

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
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

data class LlmGate(
    override val id: GateId,
    override val label: String,
    val promptTemplate: String,
    val skills: List<String> = emptyList(),
    val inputMapping: Map<String, String> = emptyMap(),
    val outputMapping: Map<String, String> = mapOf("response" to "response"),
    val endpointConfig: LlmEndpointConfig,
    val tools: List<String> = emptyList(),
    val maxToolRounds: Int = 8,
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

data class LlmEndpointConfig(
    val url: String,
    val credentialId: String = "",
    val model: String,
    val params: Map<String, Any?> = emptyMap(),
    val apiKeyVar: String? = null,
    val provider: LlmProvider = LlmProvider.OPENAI,
)

enum class LlmProvider { OPENAI, ANTHROPIC }

data class LogicGate(
    override val id: GateId,
    override val label: String,
    val branches: List<Branch>,
    val defaultPort: String? = "default",
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

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
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

data class BashGate(
    override val id: GateId,
    override val label: String,
    val command: String,
    val workingDirectory: String = ".",
    val environment: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 120,
    val failOnNonZeroExit: Boolean = true,
    val outputMapping: Map<String, String> = emptyMap(),
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

enum class WriteMode { OVERWRITE, APPEND, FAIL_IF_EXISTS }

data class ReadFileGate(
    override val id: GateId,
    override val label: String,
    val path: String,
    val outputKey: String = "content",
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}

data class WriteFileGate(
    override val id: GateId,
    override val label: String,
    val path: String,
    val contentKey: String,
    val mode: WriteMode = WriteMode.OVERWRITE,
    override val faultTolerant: Boolean = false,
) : Gate() {
    override fun withId(id: GateId): Gate = copy(id = id)
    override fun withLabel(label: String): Gate = copy(label = label)
    override fun withFaultTolerant(faultTolerant: Boolean): Gate = copy(faultTolerant = faultTolerant)
}
