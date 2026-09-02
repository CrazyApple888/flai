package me.drew.flai.ui.visual

private val PIPELINE_EXTENSIONS = listOf(".flai.yaml", ".flai.yml", ".flai", ".yaml", ".yml")
private val UNSAFE_CHARS = Regex("[^a-z0-9_-]+")
private val REPEATED_DASHES = Regex("-{2,}")

/**
 * Derives a default pipeline id from a file name: strips the pipeline
 * extension, lowercases, replaces unsafe characters with `-`, collapses
 * repeated dashes and trims them. Falls back to `pipeline` when empty.
 */
fun defaultPipelineIdFor(fileName: String): String {
    var base = fileName
    for (ext in PIPELINE_EXTENSIONS) {
        if (base.endsWith(ext, ignoreCase = true)) {
            base = base.dropLast(ext.length)
            break
        }
    }
    val sanitized = base
        .lowercase()
        .replace(UNSAFE_CHARS, "-")
        .replace(REPEATED_DASHES, "-")
        .trim('-')
    return sanitized.ifEmpty { "pipeline" }
}
