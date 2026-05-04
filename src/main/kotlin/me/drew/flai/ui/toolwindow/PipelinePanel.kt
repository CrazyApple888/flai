package me.drew.flai.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.OnePixelSplitter
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import java.awt.BorderLayout
import javax.swing.JPanel

class PipelinePanel(
    project: Project,
    service: FlaiPipelineUiService,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()) {

    private val detailPanel = PipelineDetailPanel(service, parentDisposable)

    private val listPanel = PipelineListPanel(service, parentDisposable) { selected: UiPipeline ->
        detailPanel.showPipeline(selected)
    }

    init {
        val splitter = OnePixelSplitter(false, 0.3f).apply {
            firstComponent = listPanel
            secondComponent = detailPanel
        }
        add(splitter, BorderLayout.CENTER)
    }
}
