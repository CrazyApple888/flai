package me.drew.flai.ui.visual

import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.LogicGate
import me.drew.flai.domain.model.OutputGate
import me.drew.flai.domain.model.ReadFileGate
import me.drew.flai.domain.model.ToolGate
import me.drew.flai.domain.model.WriteFileGate

/**
 * Builds the gate a palette drop starts from. Fields the user still has to fill in stay empty:
 * the YAML is written straight away and the missing values are reported by validation instead.
 */
object DefaultGateFactory {

    /** Returns null for a gate type the palette does not offer. */
    fun create(gateType: String, id: GateId): Gate? = when (gateType) {
        "input" -> InputGate(id = id, label = "Input")
        "output" -> OutputGate(id = id, label = "Output")
        "llm" -> LlmGate(
            id = id,
            label = "LLM",
            promptTemplate = "",
            endpointConfig = LlmEndpointConfig(url = "", credentialId = "", model = ""),
        )
        "logic" -> LogicGate(id = id, label = "Logic", branches = emptyList(), defaultPort = "default")
        "tool" -> ToolGate(id = id, label = "Tool", toolName = "")
        "bash" -> BashGate(id = id, label = "Bash", command = "printf hello")
        "read-file" -> ReadFileGate(id = id, label = "Read File", path = "", outputKey = "content")
        "write-file" -> WriteFileGate(id = id, label = "Write File", path = "", contentKey = "content")
        else -> null
    }

    /** Unique-enough id for a freshly dropped gate: `<type>_<epoch millis>`. */
    fun newGateId(gateType: String, timestampMillis: Long = System.currentTimeMillis()): GateId =
        GateId(gateType + "_" + timestampMillis)
}
