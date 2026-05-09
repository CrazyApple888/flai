package me.drew.flai.ui.editor

import com.intellij.icons.AllIcons
import javax.swing.Icon

object FlaiIcons {
    // Fall back to built-in icons until custom SVGs are added
    val PIPELINE_FILE: Icon = AllIcons.FileTypes.Yaml
    val GUTTER_RUN: Icon = AllIcons.Actions.Execute

    // Gate-type icons mapped to closest AllIcons equivalents
    val GATE_INPUT: Icon = AllIcons.Actions.Download
    val GATE_OUTPUT: Icon = AllIcons.Actions.Upload
    val GATE_LLM: Icon = AllIcons.Nodes.Function
    val GATE_LOGIC: Icon = AllIcons.Actions.Diff
    val GATE_TOOL: Icon = AllIcons.Nodes.Plugin
    val GATE_READ_FILE: Icon = AllIcons.Actions.MenuOpen
    val GATE_WRITE_FILE: Icon = AllIcons.Actions.Edit
}
