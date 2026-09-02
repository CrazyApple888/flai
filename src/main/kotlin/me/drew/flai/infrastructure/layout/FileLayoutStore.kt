package me.drew.flai.infrastructure.layout

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.logger
import me.drew.flai.ui.visual.GatePosition
import me.drew.flai.ui.visual.LayoutStore
import java.io.File
import java.io.IOException

private val LOG = logger<FileLayoutStore>()

/**
 * JSON sidecar-backed [LayoutStore]. File shape: `{"gates": {"<id>": {"x": 1, "y": 2}}}`.
 * Failures are non-fatal: a broken or unwritable sidecar only loses positions.
 */
class FileLayoutStore(private val sidecarFile: File) : LayoutStore {

    private class LayoutDocument(val gates: Map<String, GatePosition?>? = null)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    override fun load(): Map<String, GatePosition> {
        if (!sidecarFile.isFile) {
            return emptyMap()
        }
        return try {
            val parsed = gson.fromJson(sidecarFile.readText(), LayoutDocument::class.java)
            parsed?.gates.orEmpty().mapNotNull { (id, pos) -> pos?.let { id to it } }.toMap()
        } catch (e: IOException) {
            LOG.warn("Cannot read layout sidecar ${sidecarFile.path}", e)
            emptyMap()
        } catch (e: JsonSyntaxException) {
            LOG.warn("Ignoring malformed layout sidecar ${sidecarFile.path}", e)
            emptyMap()
        }
    }

    override fun save(positions: Map<String, GatePosition>) {
        try {
            sidecarFile.parentFile?.mkdirs()
            sidecarFile.writeText(gson.toJson(LayoutDocument(positions)))
        } catch (e: IOException) {
            LOG.warn("Cannot write layout sidecar ${sidecarFile.path}", e)
        }
    }
}
