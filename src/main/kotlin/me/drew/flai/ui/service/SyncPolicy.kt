package me.drew.flai.ui.service

import me.drew.flai.ui.visual.ValidationError

enum class SyncBlockReason {
    DOCUMENT_UNPARSABLE,
    NO_GATES,
    STRUCTURE_INVALID,
    NORMALIZE_CONFIRMATION_REQUIRED,

    /** Never produced by [SyncPolicy]; reported by the coordinator when a write attempt fails. */
    WRITE_FAILED,
}

sealed class SyncDecision {
    object Proceed : SyncDecision()
    data class Blocked(val reason: SyncBlockReason, val message: String) : SyncDecision()
}

data class SyncPreconditions(
    val documentParsable: Boolean,
    val hasNodes: Boolean,
    val structureErrors: List<ValidationError>,
    val normalizationRequired: Boolean,
    val normalizeAccepted: Boolean,
)

/** Pure decision table for automatic model → YAML writes. Checks are ordered by severity. */
object SyncPolicy {
    const val UNPARSABLE_MESSAGE = "YAML has parse errors — fix them in the text editor; visual edits are not synced"

    fun decide(preconditions: SyncPreconditions): SyncDecision {
        if (!preconditions.documentParsable) {
            return SyncDecision.Blocked(SyncBlockReason.DOCUMENT_UNPARSABLE, UNPARSABLE_MESSAGE)
        }
        if (!preconditions.hasNodes) {
            return SyncDecision.Blocked(SyncBlockReason.NO_GATES, "Add at least one gate to save")
        }
        if (preconditions.structureErrors.isNotEmpty()) {
            return SyncDecision.Blocked(
                SyncBlockReason.STRUCTURE_INVALID,
                preconditions.structureErrors.joinToString("; ") { "${it.gateId} / ${it.field}: ${it.message}" },
            )
        }
        if (preconditions.normalizationRequired && !preconditions.normalizeAccepted) {
            return SyncDecision.Blocked(
                SyncBlockReason.NORMALIZE_CONFIRMATION_REQUIRED,
                "File formatting will be normalized — press Apply once to confirm the write",
            )
        }
        return SyncDecision.Proceed
    }
}
