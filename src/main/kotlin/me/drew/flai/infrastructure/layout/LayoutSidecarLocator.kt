package me.drew.flai.infrastructure.layout

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Maps a pipeline file to its layout sidecar under the IDE system directory:
 * `<system>/flai/layouts/<project hash>/<project-relative path with separators as '_'>.layout.json`.
 * Only computes the path; nothing is created on disk.
 */
object LayoutSidecarLocator {
    fun sidecarFor(project: Project, file: VirtualFile): File {
        val relativePath = file.path
            .removePrefix(project.basePath ?: "")
            .replace('/', '_')
            .replace(File.separatorChar, '_')
            .trimStart('_')
        val layoutDir = File(PathManager.getSystemPath(), "flai/layouts/${project.locationHash}")
        return File(layoutDir, "$relativePath.layout.json")
    }
}
