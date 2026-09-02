package me.drew.flai.infrastructure.pipeline

/**
 * Decides whether a virtual-file-system event path can affect the pipelines stored in a
 * pipeline directory. Pure string logic — no IntelliJ or file-system access — so it is
 * unit-testable outside the IDE.
 *
 * A path is relevant when it is the pipeline directory itself (the directory was created,
 * deleted, renamed or moved) or when it is a direct child of that directory whose name is
 * a pipeline file name. The filter is deliberately coarse: firing too often only costs a
 * debounced reload, while missing an event leaves the tool window stale.
 */
object PipelineFileEventFilter {

    /** Returns true when [eventPath] may change the pipelines stored in [pipelineDirectoryPath]. */
    fun isRelevant(eventPath: String, pipelineDirectoryPath: String): Boolean {
        val normalizedEventPath = normalize(eventPath)
        val normalizedDirectoryPath = normalize(pipelineDirectoryPath)
        if (normalizedEventPath.isEmpty() || normalizedDirectoryPath.isEmpty()) {
            return false
        }
        if (normalizedEventPath == normalizedDirectoryPath) {
            return true
        }
        val prefix = "$normalizedDirectoryPath/"
        if (!normalizedEventPath.startsWith(prefix)) {
            return false
        }
        val relativePath = normalizedEventPath.removePrefix(prefix)
        if (relativePath.contains('/')) {
            return false
        }
        return PipelineFileNames.isPipelineFileName(relativePath)
    }

    /** Replaces Windows separators with `/` and drops trailing separators. */
    private fun normalize(path: String): String {
        val unified = path.replace('\\', '/')
        var endIndex = unified.length
        while (endIndex > 1 && unified[endIndex - 1] == '/') {
            endIndex--
        }
        return unified.substring(0, endIndex)
    }
}
