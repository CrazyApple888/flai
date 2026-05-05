package me.drew.flai.ui.editor

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import me.drew.flai.ui.actions.RunPipelineAction

class FlaiRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val file = element.containingFile ?: return null
        if (!file.name.endsWith(".flai.yaml")) return null
        // Only leaf elements (no children) and only the absolute first one
        if (element.firstChild != null) return null
        if (PsiTreeUtil.prevLeaf(element) != null) return null
        @Suppress("DEPRECATION")
        return Info(FlaiIcons.GUTTER_RUN, null, RunPipelineAction())
    }
}
