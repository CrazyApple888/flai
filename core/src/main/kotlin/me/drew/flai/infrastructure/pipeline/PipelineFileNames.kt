package me.drew.flai.infrastructure.pipeline

object PipelineFileNames {
    /** Longest first so stripping is unambiguous. */
    val EXTENSIONS: List<String> = listOf(".flai.yaml", ".flai.yml", ".flai", ".yaml", ".yml")

    private val UNSAFE_CHARS = Regex("[^a-z0-9_-]+")
    private val REPEATED_DASHES = Regex("-{2,}")

    /** Case-insensitive. */
    fun isPipelineFileName(name: String): Boolean =
        EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    /** Strips the first matching extension (case-insensitive); returns name unchanged if none matches. */
    fun stripExtension(name: String): String {
        val ext = EXTENSIONS.firstOrNull { name.endsWith(it, ignoreCase = true) } ?: return name
        return name.dropLast(ext.length)
    }

    /**
     * Derives a default pipeline id from a file name: strips the pipeline
     * extension, lowercases, replaces unsafe characters with `-`, collapses
     * repeated dashes and trims them. Falls back to `pipeline` when empty.
     */
    fun defaultPipelineIdFor(fileName: String): String {
        val sanitized = stripExtension(fileName)
            .lowercase()
            .replace(UNSAFE_CHARS, "-")
            .replace(REPEATED_DASHES, "-")
            .trim('-')
        return sanitized.ifEmpty { "pipeline" }
    }
}
