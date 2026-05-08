package me.drew.flai.ui.visual

import me.drew.flai.domain.model.Gate

data class VisualNode(
    val nodeSeq: Int,
    var gateId: String,
    var gate: Gate,
    var x: Int,
    var y: Int,
)

data class VisualEdge(
    val fromSeq: Int,
    val fromPort: String = "out",
    val toSeq: Int,
    val toPort: String = "in",
)
