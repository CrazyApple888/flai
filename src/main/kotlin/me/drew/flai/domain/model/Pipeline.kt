package me.drew.flai.domain.model

@JvmInline
value class PipelineId(val value: String)

data class Pipeline(
    val id: PipelineId,
    val name: String,
    val description: String = "",
    val gates: Map<GateId, Gate>,
    val edges: List<PipelineEdge>,
    val entryGateId: GateId,
) {
    fun nextGateId(from: GateId, port: String = "out"): GateId? =
        edges.firstOrNull { it.from == from && it.fromPort == port }?.to
}

data class PipelineEdge(
    val from: GateId,
    val fromPort: String = "out",
    val to: GateId,
    val toPort: String = "in",
)
