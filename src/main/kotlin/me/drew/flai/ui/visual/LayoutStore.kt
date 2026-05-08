package me.drew.flai.ui.visual

import org.yaml.snakeyaml.Yaml
import java.io.File

data class GatePosition(val x: Int, val y: Int)

class LayoutStore(private val sidecarFile: File) {
    private val yaml = Yaml()

    /** Returns stored positions; missing gates get null (caller uses auto-layout). */
    @Suppress("UNCHECKED_CAST")
    fun load(): Map<String, GatePosition> {
        if (!sidecarFile.exists()) return emptyMap()
        return try {
            val content = sidecarFile.readText()
            val root = yaml.load<Map<String, Any>>(content) ?: return emptyMap()
            val gates = root["gates"] as? Map<String, Any> ?: return emptyMap()
            gates.entries.mapNotNull { (id, pos) ->
                val posMap = pos as? Map<String, Any> ?: return@mapNotNull null
                val x = (posMap["x"] as? Number)?.toInt() ?: return@mapNotNull null
                val y = (posMap["y"] as? Number)?.toInt() ?: return@mapNotNull null
                id to GatePosition(x, y)
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Overwrites the sidecar file with current positions. */
    fun save(positions: Map<String, GatePosition>) {
        try {
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"gates\": {")
            val entries = positions.entries.toList()
            for ((index, entry) in entries.withIndex()) {
                val (id, pos) = entry
                val escapedId = id.replace("\"", "\\\"")
                sb.append("    \"$escapedId\": { \"x\": ${pos.x}, \"y\": ${pos.y} }")
                if (index < entries.size - 1) {
                    sb.append(",")
                }
                sb.appendLine()
            }
            sb.appendLine("  }")
            sb.append("}")
            sidecarFile.writeText(sb.toString())
        } catch (_: Exception) {
            // Silently skip if file cannot be written
        }
    }
}
