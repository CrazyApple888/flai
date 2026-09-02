package me.drew.flai.ui.model

import me.drew.flai.domain.model.PipelineId
import java.nio.file.Path

/**
 * One row of the pipeline list. A file that failed to parse is still represented, with
 * [parseError] holding the parser message and every pipeline detail left empty.
 *
 * [validationIssues] holds the human-readable messages of the validation issues a *parseable*
 * pipeline still has (unconfigured gates, broken edges, cycles). A file that does not parse has
 * [parseError] set and no [validationIssues], because there is no pipeline to validate.
 */
data class UiPipeline(
    val id: PipelineId,
    val name: String,
    val description: String,
    val gateCount: Int,
    val filePath: Path?,
    val inputSpecs: List<InputFieldSpec>,
    val parseError: String? = null,
    val validationIssues: List<String> = emptyList(),
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
    val gateId: String? = null,
    val durationMs: Long? = null,
    val message: String? = null,
    val outputLabel: String? = null,
    val outputValue: String? = null,
    val isNested: Boolean = false,
)

enum class GateStatus { RUNNING, SUCCESS, FAILURE, OUTPUT, TOLERATED_FAILURE }

sealed class ExecutionUiState {
    object Idle : ExecutionUiState()
    object Running : ExecutionUiState()
    data class Completed(val outputs: Map<String, Any?>) : ExecutionUiState()
    data class Failed(val reason: String) : ExecutionUiState()
}
