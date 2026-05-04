package me.drew.flai.ui.editor

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.yaml.YAMLLanguage
import javax.swing.Icon

class FlaiYamlFileType private constructor() : LanguageFileType(YAMLLanguage.INSTANCE) {
    companion object {
        @JvmField
        val INSTANCE = FlaiYamlFileType()
    }

    override fun getName() = "Flai Pipeline"
    override fun getDescription() = "flai agentic pipeline definition"
    override fun getDefaultExtension() = "flai.yaml"
    override fun getIcon(): Icon = FlaiIcons.PIPELINE_FILE
}
