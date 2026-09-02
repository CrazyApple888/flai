package me.drew.flai.infrastructure.preferences

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Per-project, per-file visual editor choices stored in the project [PropertiesComponent]. */
class VisualEditorPreferences(private val project: Project) {

    /** True once the user accepted that writing this file normalizes its YAML. */
    fun isNormalizeAccepted(file: VirtualFile): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(normalizeKey(file), false)

    fun acceptNormalize(file: VirtualFile) {
        PropertiesComponent.getInstance(project).setValue(normalizeKey(file), true)
    }

    private fun normalizeKey(file: VirtualFile): String {
        val relativePath = file.path
            .removePrefix(project.basePath ?: "")
            .trimStart('/')
        return "flai.apply.warned.$relativePath"
    }
}
