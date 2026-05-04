package me.drew.flai.ui.editor

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import me.drew.flai.ui.actions.RunPipelineAction

class FlaiRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val file = element.containingFile ?: return null
        if (!file.name.endsWith(".flai.yaml")) return null
        // Mark only the first token of the file (offset == 0)
        if (element.textOffset != 0) return null
        @Suppress("DEPRECATION")
        return Info(FlaiIcons.GUTTER_RUN, null, RunPipelineAction())
    }
}
