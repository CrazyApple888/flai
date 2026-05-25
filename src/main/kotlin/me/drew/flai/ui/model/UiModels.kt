package me.drew.flai.ui.model

import me.drew.flai.domain.model.PipelineId
import java.nio.file.Path

data class UiPipeline(
    val id: PipelineId,
    val name: String,
    val description: String,
    val gateCount: Int,
    val filePath: Path?,
    val inputSpecs: List<InputFieldSpec>,
)

data class InputFieldSpec(
    val key: String,
    val label: String,
    val defaultValue: String,
    val required: Boolean,
)

data class GateRow(
    val gateName: String,
    val status: GateStatus,
    val durationMs: Long? = null,
    val message: String? = null,
    val outputLabel: String? = null,
    val outputValue: String? = null,
)

enum class GateStatus { RUNNING, SUCCESS, FAILURE, OUTPUT }

sealed class ExecutionUiState {
    object Idle : ExecutionUiState()
    object Running : ExecutionUiState()
    data class Completed(val outputs: Map<String, Any?>) : ExecutionUiState()
    data class Failed(val reason: String) : ExecutionUiState()
}
