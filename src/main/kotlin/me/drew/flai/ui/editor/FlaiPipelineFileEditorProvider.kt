package me.drew.flai.ui.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JLabel
import javax.swing.JPanel

private val LOG = logger<FlaiPipelineFileEditorProvider>()

class FlaiPipelineFileEditorProvider : FileEditorProvider, DumbAware {
    override fun getEditorTypeId(): String = "flai-visual-pipeline"

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".flai.yaml") || file.name.endsWith(".flai")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return try {
            FlaiPipelineFileEditor(project, file)
        } catch (e: Exception) {
            LOG.error("Failed to create visual pipeline editor for ${file.name}", e)
            ErrorPlaceholderEditor(file, e.message ?: "Unknown error")
        }
    }

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class ErrorPlaceholderEditor(
    private val file: VirtualFile,
    message: String,
) : UserDataHolderBase(), FileEditor {
    private val panel = JPanel().apply {
        add(JLabel("Visual editor failed to load: $message"))
    }
    override fun getComponent() = panel
    override fun getPreferredFocusedComponent() = panel
    override fun getName() = "Visual"
    override fun isModified() = false
    override fun isValid() = true
    override fun getFile() = file
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState { _, _ -> false }
    override fun setState(state: FileEditorState) {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
}
