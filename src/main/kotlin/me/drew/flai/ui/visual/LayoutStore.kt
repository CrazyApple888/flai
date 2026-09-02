package me.drew.flai.ui.visual

data class GatePosition(val x: Int, val y: Int)

/** Persists canvas node positions keyed by gate id, outside the pipeline YAML. */
interface LayoutStore {
    /** Returns stored positions; gates without an entry are laid out automatically by the caller. */
    fun load(): Map<String, GatePosition>

    /** Replaces the stored positions with [positions]. */
    fun save(positions: Map<String, GatePosition>)
}
