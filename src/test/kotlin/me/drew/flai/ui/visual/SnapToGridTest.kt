package me.drew.flai.ui.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapToGridTest {

    @Test
    fun `value exactly on grid stays on grid`() {
        assertEquals(40, snapToGrid(40, 20))
        assertEquals(0, snapToGrid(0, 20))
        assertEquals(20, snapToGrid(20, 20))
    }

    @Test
    fun `value below midpoint rounds down to grid`() {
        assertEquals(40, snapToGrid(49, 20))
        assertEquals(20, snapToGrid(29, 20))
    }

    @Test
    fun `value at midpoint rounds up`() {
        // Math.round rounds half-up for positive values
        assertEquals(60, snapToGrid(50, 20))
        assertEquals(40, snapToGrid(30, 20))
    }

    @Test
    fun `value above midpoint rounds up to next grid`() {
        assertEquals(60, snapToGrid(51, 20))
        assertEquals(80, snapToGrid(71, 20))
    }

    @Test
    fun `gridSize of 1 snaps any value to itself`() {
        assertEquals(17, snapToGrid(17, 1))
        assertEquals(0, snapToGrid(0, 1))
    }

    @Test
    fun `negative value snaps correctly`() {
        assertEquals(-40, snapToGrid(-40, 20))
        assertEquals(-40, snapToGrid(-41, 20))
        assertEquals(-20, snapToGrid(-21, 20))
    }
}
