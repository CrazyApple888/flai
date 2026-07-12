package me.drew.flai.ui.visual

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `node accent stripe does not paint outside the rounded node corners`() {
        val model = VisualPipelineModel()
        val gate = InputGate(id = GateId("g"), label = "G")
        model.addNode(gate, 20, 50)

        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics() as Graphics2D
        g2.color = Color.BLACK
        g2.fillRect(0, 0, 400, 200)

        CanvasRenderer().paint(g2, model, emptyRenderState())
        g2.dispose()

        // Node body is a rounded rect with arc $ARC. Every accent-colored pixel in
        // the stripe column must lie inside the body shape — none may leak past
        // the rounded top-left/bottom-left corners
        val accent = FlaiEditorTheme.accentFor(gate).rgb
        val body = java.awt.geom.RoundRectangle2D.Float(
            20f, 50f, NODE_WIDTH.toFloat(), NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat()
        )
        for (px in 20 until 24) {
            for (py in 50 until 50 + NODE_HEIGHT) {
                if (image.getRGB(px, py) == accent) {
                    assertTrue(
                        "Stripe leaked outside the rounded body at ($px, $py)",
                        body.contains(px + 0.5, py + 0.5),
                    )
                }
            }
        }
        assertEquals("Accent stripe missing inside the node body", accent, image.getRGB(22, 80))
    }

    private fun nodeBodyShape(x: Int, y: Int) = java.awt.geom.RoundRectangle2D.Float(
        x.toFloat(), y.toFloat(), NODE_WIDTH.toFloat(), NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat()
    )

    private fun assertColorInsideBody(image: BufferedImage, colors: Set<Int>, x: Int, y: Int, what: String) {
        val body = nodeBodyShape(x, y)
        var found = false
        for (px in 0 until image.width) {
            for (py in 0 until image.height) {
                if (image.getRGB(px, py) in colors) {
                    found = true
                    assertTrue(
                        "$what leaked outside the rounded body at ($px, $py)",
                        body.contains(px + 0.5, py + 0.5),
                    )
                }
            }
        }
        assertTrue("$what pixels not found at all", found)
    }

    @Test
    fun `llm star stays inside the rounded node body`() {
        val model = VisualPipelineModel()
        val gate = LlmGate(
            id = GateId("g"),
            label = "G",
            promptTemplate = "x",
            endpointConfig = LlmEndpointConfig("url", "cred", "model"),
        )
        model.addNode(gate, 50, 50)

        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics() as Graphics2D
        g2.color = Color.BLACK
        g2.fillRect(0, 0, 400, 200)
        CanvasRenderer().paint(g2, model, emptyRenderState())
        g2.dispose()

        val starColors = setOf(Color(220, 180, 30).rgb, Color(255, 210, 60).rgb)
        assertColorInsideBody(image, starColors, 50, 50, "LLM star")
    }

    @Test
    fun `entry badge stays inside the rounded node body`() {
        val model = VisualPipelineModel()
        val node = model.addNode(InputGate(id = GateId("g"), label = "G"), 50, 50)
        model.setEntry(node.nodeSeq)

        val image = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics() as Graphics2D
        g2.color = Color.BLACK
        g2.fillRect(0, 0, 400, 200)
        CanvasRenderer().paint(g2, model, emptyRenderState())
        g2.dispose()

        val badgeColors = setOf(Color(200, 100, 0).rgb, Color(255, 170, 40).rgb)
        assertColorInsideBody(image, badgeColors, 50, 50, "Entry badge")
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
