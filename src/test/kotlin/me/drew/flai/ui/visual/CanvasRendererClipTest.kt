package me.drew.flai.ui.visual

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasRendererClipTest {

    private fun emptyRenderState() = CanvasRenderState(
        selectedNodeSeq = -1,
        hoveredNodeSeq = -1,
        nodeHoverAlpha = emptyMap(),
        executionStatus = emptyMap(),
        isGhostDragging = false,
        ghostNodeSeq = -1,
        ghostX = 0,
        ghostY = 0,
        isDraggingEdge = false,
        edgeDragFromSeq = -1,
        edgeDragFromPort = "",
        edgeDragModelX = 0.0,
        edgeDragModelY = 0.0,
        animTick = 0,
        selectedEdge = null,
    )

    @Test
    fun `node accent stripe does not paint outside the graphics clip`() {
        val model = VisualPipelineModel()
        model.addNode(InputGate(id = GateId("g"), label = "G"), 200, 50)

        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics() as Graphics2D
        g2.color = Color.BLACK
        g2.fillRect(0, 0, 400, 200)
        // Simulate the component-bounds clip Swing applies before paintComponent:
        // only the left half of the buffer belongs to the canvas
        g2.clip = Rectangle(0, 0, 150, 200)

        CanvasRenderer().paint(g2, model, emptyRenderState())
        g2.dispose()

        // Accent stripe spans x=200..203; it lies fully outside the clip
        val stripePixel = image.getRGB(202, 80)
        assertEquals(
            "Accent stripe painted outside the clip region",
            Color.BLACK.rgb,
            stripePixel,
        )
    }

    @Test
    fun `node accent stripe still paints inside the graphics clip`() {
        val model = VisualPipelineModel()
        val gate = InputGate(id = GateId("g"), label = "G")
        model.addNode(gate, 20, 50)

        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics() as Graphics2D
        g2.color = Color.BLACK
        g2.fillRect(0, 0, 400, 200)
        g2.clip = Rectangle(0, 0, 400, 200)

        CanvasRenderer().paint(g2, model, emptyRenderState())
        g2.dispose()

        val expected = FlaiEditorTheme.accentFor(gate).rgb
        val stripePixel = image.getRGB(22, 80)
        assertEquals(
            "Accent stripe missing inside the clip region",
            expected,
            stripePixel,
        )
    }
}
