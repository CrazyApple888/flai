package me.drew.flai.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import me.drew.flai.ui.editor.FlaiIcons
import me.drew.flai.ui.service.FlaiPipelineUiService

class RunPipelineAction : AnAction("Run Pipeline", "Run the selected flai pipeline", FlaiIcons.GUTTER_RUN) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null &&
            file != null && file.name.endsWith(".flai.yaml")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = project.getService(FlaiPipelineUiService::class.java) ?: return

        // Always open the tool window first
        ToolWindowManager.getInstance(project).getToolWindow("FlaiPipelines")?.activate {
            service.runFromFile(file.path)
        }
    }
}
