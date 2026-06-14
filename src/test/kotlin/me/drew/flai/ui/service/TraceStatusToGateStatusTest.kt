package me.drew.flai.ui.service

import me.drew.flai.domain.model.TraceStatus
import me.drew.flai.ui.model.GateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TraceStatusToGateStatusTest {

    @Test
    fun `tolerated failure maps to TOLERATED_FAILURE never SUCCESS (FR-9 AC-7)`() {
        val mapped = traceStatusToGateStatus(TraceStatus.TOLERATED_FAILURE)
        assertEquals(GateStatus.TOLERATED_FAILURE, mapped)
        assertNotEquals(GateStatus.SUCCESS, mapped)
    }

    @Test
    fun `failure maps to FAILURE`() {
        assertEquals(GateStatus.FAILURE, traceStatusToGateStatus(TraceStatus.FAILURE))
    }

    @Test
    fun `success maps to SUCCESS`() {
        assertEquals(GateStatus.SUCCESS, traceStatusToGateStatus(TraceStatus.SUCCESS))
    }

    @Test
    fun `failure and tolerated failure map to distinct statuses`() {
        assertNotEquals(
            traceStatusToGateStatus(TraceStatus.FAILURE),
            traceStatusToGateStatus(TraceStatus.TOLERATED_FAILURE),
        )
    }
}
