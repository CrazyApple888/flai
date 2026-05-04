package me.drew.flai.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import me.drew.flai.infrastructure.tool.*
import me.drew.flai.ui.service.FlaiPipelineUiService

class FlaiStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val uiService = project.getService(FlaiPipelineUiService::class.java) ?: return
        val registry = uiService.toolRegistry
        registry.register(PsiSymbolSearchTool(project))
        registry.register(FileReadTool(project))
        registry.register(RunCommandTool(project))
        uiService.refresh()
    }
}
