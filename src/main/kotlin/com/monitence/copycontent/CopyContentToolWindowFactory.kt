package com.monitence.copycontent

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CopyContentToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(CopyContentService::class.java)

        val panel = CopyContentPanel(project)
        service.panel = panel

        // garante que o painel reflita o conteúdo acumulado atual
        panel.setText(service.getText())

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
