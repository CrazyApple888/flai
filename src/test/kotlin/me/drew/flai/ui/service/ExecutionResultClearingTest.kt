package me.drew.flai.ui.service

import me.drew.flai.domain.model.PipelineId
import me.drew.flai.ui.model.ExecutionUiState
import me.drew.flai.ui.model.UiPipeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/** Covers [shouldClearExecutionResult] — the rule that hides a stale run failure on selection change. */
class ExecutionResultClearingTest {

    private fun pipeline(
        id: String,
        filePath: Path? = Path.of("/project/.flai/$id.flai.yaml"),
    ) = UiPipeline(
        id = PipelineId(id),
        name = id,
        description = "",
        gateCount = 3,
        filePath = filePath,
        inputSpecs = emptyList(),
    )

    @Test
    fun `selecting a different pipeline clears the previous result`() {
        assertTrue(
            shouldClearExecutionResult(
                pipeline("first"),
                pipeline("second"),
                ExecutionUiState.Failed("boom"),
            ),
        )
    }

    @Test
    fun `re-selecting the same pipeline id keeps the result`() {
        assertFalse(
            shouldClearExecutionResult(
                pipeline("demo"),
                pipeline("demo").copy(gateCount = 9),
                ExecutionUiState.Failed("boom"),
            ),
        )
    }

    @Test
    fun `a reloaded row of the same file keeps the result even when the pipeline id changed`() {
        val filePath = Path.of("/project/.flai/demo.flai.yaml")
        assertFalse(
            shouldClearExecutionResult(
                pipeline("old-id", filePath),
                pipeline("new-id", filePath),
                ExecutionUiState.Completed(emptyMap()),
            ),
        )
    }

    @Test
    fun `two rows without a file path are told apart by their ids`() {
        assertTrue(
            shouldClearExecutionResult(
                pipeline("first", filePath = null),
                pipeline("second", filePath = null),
                ExecutionUiState.Failed("boom"),
            ),
        )
    }

    @Test
    fun `a run in progress is never cleared`() {
        assertFalse(
            shouldClearExecutionResult(
                pipeline("first"),
                pipeline("second"),
                ExecutionUiState.Running,
            ),
        )
    }

    @Test
    fun `a run in progress survives even a selection with no previous row`() {
        assertFalse(shouldClearExecutionResult(null, pipeline("second"), ExecutionUiState.Running))
    }

    @Test
    fun `selecting a row after the selection was cleared drops the stale result`() {
        assertTrue(shouldClearExecutionResult(null, pipeline("second"), ExecutionUiState.Failed("boom")))
    }

    @Test
    fun `an idle state is cleared as well, which is a no-op`() {
        assertTrue(
            shouldClearExecutionResult(
                pipeline("first"),
                pipeline("second"),
                ExecutionUiState.Idle,
            ),
        )
    }
}
