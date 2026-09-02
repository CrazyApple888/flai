package me.drew.flai.ui.service

import me.drew.flai.ui.visual.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {

    private val structureError = ValidationError("(pipeline)", "entry", "Pipeline entry gate is required and must exist")

    private fun ready(
        documentParsable: Boolean = true,
        hasNodes: Boolean = true,
        structureErrors: List<ValidationError> = emptyList(),
        normalizationRequired: Boolean = false,
        normalizeAccepted: Boolean = false,
    ) = SyncPreconditions(documentParsable, hasNodes, structureErrors, normalizationRequired, normalizeAccepted)

    private fun blockedReason(decision: SyncDecision): SyncBlockReason {
        assertTrue("expected Blocked but was $decision", decision is SyncDecision.Blocked)
        return (decision as SyncDecision.Blocked).reason
    }

    @Test
    fun `proceeds when everything is in order`() {
        assertEquals(SyncDecision.Proceed, SyncPolicy.decide(ready()))
    }

    @Test
    fun `proceeds when normalization is required but already accepted`() {
        val decision = SyncPolicy.decide(ready(normalizationRequired = true, normalizeAccepted = true))
        assertEquals(SyncDecision.Proceed, decision)
    }

    @Test
    fun `blocks on unparsable document`() {
        val decision = SyncPolicy.decide(ready(documentParsable = false))
        assertEquals(SyncBlockReason.DOCUMENT_UNPARSABLE, blockedReason(decision))
        assertTrue((decision as SyncDecision.Blocked).message.contains("parse errors"))
    }

    @Test
    fun `blocks when there are no gates`() {
        val decision = SyncPolicy.decide(ready(hasNodes = false))
        assertEquals(SyncBlockReason.NO_GATES, blockedReason(decision))
    }

    @Test
    fun `blocks on structure errors and lists them in the message`() {
        val decision = SyncPolicy.decide(ready(structureErrors = listOf(structureError)))
        assertEquals(SyncBlockReason.STRUCTURE_INVALID, blockedReason(decision))
        assertEquals("(pipeline) / entry: ${structureError.message}", (decision as SyncDecision.Blocked).message)
    }

    @Test
    fun `blocks when normalization is required and not yet accepted`() {
        val decision = SyncPolicy.decide(ready(normalizationRequired = true))
        assertEquals(SyncBlockReason.NORMALIZE_CONFIRMATION_REQUIRED, blockedReason(decision))
    }

    @Test
    fun `unparsable document wins over missing gates`() {
        val decision = SyncPolicy.decide(ready(documentParsable = false, hasNodes = false))
        assertEquals(SyncBlockReason.DOCUMENT_UNPARSABLE, blockedReason(decision))
    }

    @Test
    fun `missing gates win over structure errors`() {
        val decision = SyncPolicy.decide(ready(hasNodes = false, structureErrors = listOf(structureError)))
        assertEquals(SyncBlockReason.NO_GATES, blockedReason(decision))
    }

    @Test
    fun `structure errors win over normalization confirmation`() {
        val decision = SyncPolicy.decide(ready(structureErrors = listOf(structureError), normalizationRequired = true))
        assertEquals(SyncBlockReason.STRUCTURE_INVALID, blockedReason(decision))
    }
}
