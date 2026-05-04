package me.drew.flai.infrastructure.tool

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool

class PsiSymbolSearchTool(private val project: Project) : Tool {
    override val name = "ide.searchSymbol"
    override val description = "Search for files/symbols by name using IDE index"

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        val query = inputs["query"]?.toString() ?: return mapOf("symbols" to emptyList<Any>())
        val scopeArg = inputs["scope"]?.toString()
        val scope = if (scopeArg == "all") GlobalSearchScope.allScope(project)
                    else GlobalSearchScope.projectScope(project)

        val results = smartReadAction(project) {
            val psiManager = PsiManager.getInstance(project)
            FilenameIndex.getAllFilenames(project)
                .filter { it.contains(query, ignoreCase = true) }
                .flatMap { filename ->
                    FilenameIndex.getVirtualFilesByName(filename, scope)
                        .mapNotNull { vf ->
                            val psiFile = psiManager.findFile(vf)
                            mapOf(
                                "name" to filename,
                                "kind" to "file",
                                "path" to vf.path,
                                "language" to (psiFile?.language?.displayName ?: "unknown"),
                            )
                        }
                }
                .take(50)
        }

        return mapOf("symbols" to results, "count" to results.size)
    }
}
